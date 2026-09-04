package com.yzytech.scx.ocpx.rta.application;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.adapter.RtaDecisionAdapter;
import com.yzytech.scx.ocpx.rta.admission.RtaAdmissionStore;
import com.yzytech.scx.ocpx.rta.config.RtaProperties;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.exception.RtaConflictException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import com.yzytech.scx.ocpx.rta.model.RtaAdapterResult;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionEvent;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionResponse;
import com.yzytech.scx.ocpx.rta.model.RtaProfileSnapshot;
import com.yzytech.scx.ocpx.rta.stream.RtaStreamPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;



/** RTA 请求校验、Mock 决策、短期 admission 和异步事实入队。 */
@Service
public class RtaDecisionService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final List<String> ATTRIBUTE_ALLOWLIST = List.of("channel", "campaign", "placement", "source");

	private final JdbcTemplate jdbcTemplate;

	private final RtaProperties properties;

	private final RtaAdmissionStore admissionStore;

	private final RtaStreamPublisher streamPublisher;

	private final Map<String, RtaDecisionAdapter> adapters;

	private MeterRegistry meterRegistry;

	private OcpxFlowLogWriter flowLogWriter;

	public RtaDecisionService(JdbcTemplate jdbcTemplate, RtaProperties properties, RtaAdmissionStore admissionStore,
			RtaStreamPublisher streamPublisher, List<RtaDecisionAdapter> adapterList) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
		this.admissionStore = admissionStore;
		this.streamPublisher = streamPublisher;
		this.adapters = new LinkedHashMap<>();
		adapterList.forEach(adapter -> this.adapters.put(adapter.code(), adapter));
	}

	@Autowired(required = false)
	void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Autowired(required = false)
	void setFlowLogWriter(OcpxFlowLogWriter flowLogWriter) {
		this.flowLogWriter = flowLogWriter;
	}

	public RtaDecisionResponse decide(RtaDecisionRequest request) {
		validate(request);
		counter("rta_requests_total", "source", "internal");
		String requestHash = hash(request);
		TaskRow task = requireTask(request);
		if (!properties.isEnabled()) {
			return new RtaDecisionResponse(request.getRequestId(), RtaDecision.DENY, List.of(), "RTA_DISABLED");
		}
		if (!task.rtaEnabled()) {
			return new RtaDecisionResponse(request.getRequestId(), RtaDecision.DENY, List.of(), "RTA_DISABLED");
		}
		String lockToken = admissionStore.acquire(request.getMonitorTaskId(), request.getRequestId(), 30_000);
		if (lockToken == null) {
			throw new RtaInfrastructureException("RTA request is already being decided");
		}
		try {
			RtaAdmissionStore.Admission cached = null;
			try {
				cached = admissionStore.find(request.getMonitorTaskId(), request.getRequestId());
			}
			catch (RuntimeException exception) {
				if (properties.isEnabled()) {
					throw new RtaInfrastructureException("RTA admission storage unavailable", exception);
				}
			}
			if (cached != null) {
				if (!requestHash.equals(cached.getRequestHash())) {
					throw new RtaConflictException("requestId 已对应其他 RTA 请求");
				}
				return response(cached);
			}
			DecisionFactRow fact = existingFact(request);
			if (fact != null) {
				if (!requestHash.equals(fact.requestHash())) {
					throw new RtaConflictException("requestId 已对应其他 RTA 请求");
				}
				RtaAdmissionStore.Admission replay = new RtaAdmissionStore.Admission(request.getRequestId(), requestHash,
						request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId(),
						request.getMonitorTaskId(), fact.profileId(), fact.decision(), fact.reasonCode(), fact.taskIds(),
						Instant.now().plusSeconds(Math.max(1, properties.getAdmissionTtlSeconds())).getEpochSecond());
				cache(replay);
				return response(replay);
			}

			LocalDateTime receivedAt = LocalDateTime.now(BUSINESS_ZONE);
			List<BindingRow> bindings = bindings(request);
			if (bindings.isEmpty()) {
				return publishAndCache(request, requestHash, null, "MOCK", new RtaAdapterResult(RtaDecision.DENY,
						"SUCCESS", "RTA_UNBOUND", List.of()), receivedAt);
			}
			if (bindings.stream().map(BindingRow::profileId).distinct().count() > 1) {
				return publishAndCache(request, requestHash, null, "MOCK", new RtaAdapterResult(RtaDecision.DENY,
						"REJECTED", "RTA_BINDING_CONFLICT", List.of()), receivedAt);
			}
			BindingRow first = bindings.get(0);
			RtaProfileSnapshot profile = new RtaProfileSnapshot(first.profileId(), first.adapterCode(), first.configJson());
			RtaDecisionAdapter adapter = adapters.get(profile.adapterCode());
			if (adapter == null || !"MOCK".equals(profile.adapterCode()) || !properties.isMockEnabled()) {
				return publishAndCache(request, requestHash, profile.id(), profile.adapterCode(),
						new RtaAdapterResult(RtaDecision.DENY, "ERROR", "RTA_ADAPTER_UNSUPPORTED", List.of()), receivedAt);
			}
			List<String> taskIds = bindings.stream().map(BindingRow::externalTaskId).filter(StringUtils::hasText)
				.distinct().toList();
			long started = System.nanoTime();
			RtaAdapterResult result = adapter.decide(request, profile, taskIds);
			long latency = Math.max(0, (System.nanoTime() - started) / 1_000_000);
			RtaDecisionEvent event = event(request, requestHash, profile.id(), profile.adapterCode(), result, latency,
					receivedAt);
			publish(event);
			RtaAdmissionStore.Admission admission = new RtaAdmissionStore.Admission(request.getRequestId(), requestHash,
					request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId(), request.getMonitorTaskId(),
					profile.id(), result.decision(), result.reasonCode(), result.taskIds(),
					Instant.now().plusSeconds(Math.max(1, properties.getAdmissionTtlSeconds())).getEpochSecond());
			cache(admission);
			counter("rta_decisions_total", "decision", result.decision().name());
			return response(admission);
		}
		finally {
			admissionStore.release(request.getMonitorTaskId(), request.getRequestId(), lockToken);
		}
	}

	private RtaDecisionResponse publishAndCache(RtaDecisionRequest request, String requestHash, String profileId,
			String adapterCode, RtaAdapterResult result, LocalDateTime receivedAt) {
		RtaDecisionEvent event = event(request, requestHash, profileId, adapterCode, result, 0, receivedAt);
		publish(event);
		RtaAdmissionStore.Admission admission = new RtaAdmissionStore.Admission(request.getRequestId(), requestHash,
				request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId(), request.getMonitorTaskId(),
				profileId, result.decision(), result.reasonCode(), result.taskIds(),
				Instant.now().plusSeconds(Math.max(1, properties.getAdmissionTtlSeconds())).getEpochSecond());
		cache(admission);
		counter("rta_decisions_total", "decision", result.decision().name());
		return response(admission);
	}

	private void publish(RtaDecisionEvent event) {
		streamPublisher.publish(event);
		if (flowLogWriter != null) {
			flowLogWriter.rtaDecision(event);
		}
	}

	private void cache(RtaAdmissionStore.Admission admission) {
		try {
			admissionStore.save(admission, properties.getAdmissionTtlSeconds());
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA admission storage unavailable", exception);
		}
	}

	private RtaDecisionEvent event(RtaDecisionRequest request, String requestHash, String profileId,
			String adapterCode, RtaAdapterResult result, long latency, LocalDateTime receivedAt) {
		return new RtaDecisionEvent(request.getRequestId(), requestHash, request.getAdvertiserId(),
				request.getPromotionLinkId(), request.getMediaId(), request.getMonitorTaskId(), profileId, adapterCode,
				result.decision(), result.outcomeClass(), result.reasonCode(), latency, result.taskIds(), receivedAt,
				LocalDateTime.now(BUSINESS_ZONE));
	}

	private RtaDecisionResponse response(RtaAdmissionStore.Admission admission) {
		return new RtaDecisionResponse(admission.getRequestId(), admission.getDecision(),
				admission.getTaskIds() == null ? List.of() : admission.getTaskIds(), admission.getReasonCode());
	}

	private TaskRow requireTask(RtaDecisionRequest request) {
		List<TaskRow> rows;
		try {
			rows = jdbcTemplate.query("""
					SELECT id,advertiser_id,promotion_link_id,media_id,rta_enabled
					FROM scx_ocpx_monitor_task
					WHERE id=? AND advertiser_id=? AND promotion_link_id=? AND media_id=? AND is_delete=0
					""", (rs, rowNum) -> new TaskRow(rs.getString("id"), rs.getString("advertiser_id"),
							rs.getString("promotion_link_id"), rs.getString("media_id"), rs.getBoolean("rta_enabled")),
				request.getMonitorTaskId(), request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA monitor task storage unavailable", exception);
		}
		if (rows.size() != 1) {
			throw new IllegalArgumentException("monitor task does not match advertiser, promotion link and media");
		}
		return rows.get(0);
	}

	private List<BindingRow> bindings(RtaDecisionRequest request) {
		try {
			return jdbcTemplate.query("""
					SELECT b.profile_id,p.adapter_code,p.config_json,b.external_task_id
					FROM scx_ocpx_rta_task_binding b
					JOIN scx_ocpx_rta_profile p ON p.id=b.profile_id
					WHERE b.monitor_task_id=? AND b.advertiser_id=? AND b.promotion_link_id=? AND b.media_id=?
					  AND b.enabled=1 AND p.status='ACTIVE'
					ORDER BY b.id
					""", (rs, rowNum) -> new BindingRow(rs.getString("profile_id"), rs.getString("adapter_code"),
							rs.getString("config_json"), rs.getString("external_task_id")), request.getMonitorTaskId(),
					request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA binding storage unavailable", exception);
		}
	}

	private DecisionFactRow existingFact(RtaDecisionRequest request) {
		try {
			List<DecisionFactRow> rows = jdbcTemplate.query("""
					SELECT request_hash,profile_id,decision,reason_code,matched_task_ids
					FROM scx_ocpx_rta_decision_fact
					WHERE monitor_task_id=? AND request_id=?
					""", (rs, rowNum) -> new DecisionFactRow(rs.getString("request_hash"), rs.getString("profile_id"),
							RtaDecision.valueOf(rs.getString("decision")), rs.getString("reason_code"),
							JSON.parseArray(rs.getString("matched_task_ids"), String.class)), request.getMonitorTaskId(),
					request.getRequestId());
			return rows.isEmpty() ? null : rows.get(0);
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA decision fact storage unavailable", exception);
		}
	}

	private void validate(RtaDecisionRequest request) {
		if (request == null || !StringUtils.hasText(request.getRequestId())
				|| !StringUtils.hasText(request.getAdvertiserId()) || !StringUtils.hasText(request.getPromotionLinkId())
				|| !StringUtils.hasText(request.getMediaId()) || !StringUtils.hasText(request.getMonitorTaskId())) {
			throw new IllegalArgumentException("requestId and OCPX route IDs are required");
		}
		validateLength(request.getRequestId(), 128, "requestId length must be between 1 and 128");
		validateLength(request.getAdvertiserId(), 32, "advertiserId length exceeds 32");
		validateLength(request.getPromotionLinkId(), 32, "promotionLinkId length exceeds 32");
		validateLength(request.getMediaId(), 32, "mediaId length exceeds 32");
		validateLength(request.getMonitorTaskId(), 32, "monitorTaskId length exceeds 32");
		RtaDecisionRequest.Device device = request.getDevice();
		if (device != null) {
			validateLength(device.getOaid(), 256, "device field length exceeds 256");
			validateLength(device.getOaidMd5(), 64, "device field length exceeds 64");
			validateLength(device.getImei(), 256, "device field length exceeds 256");
			validateLength(device.getImeiMd5(), 64, "device field length exceeds 64");
			validateLength(device.getIdfa(), 256, "device field length exceeds 256");
			validateLength(device.getIdfaMd5(), 64, "device field length exceeds 64");
			validateLength(device.getCaid(), 256, "device field length exceeds 256");
			validateLength(device.getCaidVersion(), 64, "device field length exceeds 64");
			validateLength(device.getPreCaid(), 256, "device field length exceeds 256");
			validateLength(device.getPreCaidVersion(), 64, "device field length exceeds 64");
		}
		RtaDecisionRequest.Context context = request.getContext();
		if (context != null) {
			validateLength(context.getIp(), 64, "context field length exceeds 64");
			validateLength(context.getIpv6(), 128, "context field length exceeds 128");
			validateLength(context.getOs(), 32, "context field length exceeds 32");
			validateLength(context.getOsv(), 64, "context field length exceeds 64");
			validateLength(context.getModel(), 128, "context field length exceeds 128");
			validateLength(context.getUa(), 1024, "context field length exceeds 1024");
		}
		if (request.getAttributes() != null) {
			if (request.getAttributes().size() > 16 || request.getAttributes().keySet().stream()
					.anyMatch(key -> key == null || !ATTRIBUTE_ALLOWLIST.contains(key))) {
				throw new IllegalArgumentException("RTA attributes contain unsupported fields");
			}
			request.getAttributes().forEach((key, value) -> {
				validateLength(key, 64, "RTA attribute key length exceeds 64");
				validateLength(value, 256, "RTA attribute value length exceeds 256");
			});
			int attributesBytes = JSON.toJSONString(request.getAttributes()).getBytes(StandardCharsets.UTF_8).length;
			if (attributesBytes > 8 * 1024) {
				throw new IllegalArgumentException("RTA attributes exceed 8 KB");
			}
		}
		if (JSON.toJSONString(request).getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
			throw new IllegalArgumentException("RTA request exceeds 64 KB");
		}
	}

	private void validateLength(String value, int max, String message) {
		if (value != null && value.length() > max) {
			throw new IllegalArgumentException(message);
		}
	}

	private String hash(RtaDecisionRequest request) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		normalized.put("requestId", request.getRequestId());
		normalized.put("advertiserId", request.getAdvertiserId());
		normalized.put("promotionLinkId", request.getPromotionLinkId());
		normalized.put("mediaId", request.getMediaId());
		normalized.put("monitorTaskId", request.getMonitorTaskId());
		normalized.put("device", request.getDevice());
		normalized.put("context", request.getContext());
		normalized.put("attributes", request.getAttributes() == null ? Map.of() : new TreeMap<>(request.getAttributes()));
		try {
			byte[] bytes = JSON.toJSONString(normalized).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to hash RTA request", exception);
		}
	}

	private void counter(String name, String tag, String value) {
		if (meterRegistry != null) {
			meterRegistry.counter(name, tag, value).increment();
		}
	}

	private record TaskRow(String id, String advertiserId, String promotionLinkId, String mediaId,
			boolean rtaEnabled) {
	}

	private record BindingRow(String profileId, String adapterCode, String configJson, String externalTaskId) {
	}

	private record DecisionFactRow(String requestHash, String profileId, RtaDecision decision, String reasonCode,
			List<String> taskIds) {
	}

}
