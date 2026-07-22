package com.yzytech.scx.tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtaDecisionService {

	private final RtaConfigurationCache configurationCache;

	private final RtaDecisionLogBuffer logBuffer;

	private final RtaTransport transport;

	private final ObjectMapper objectMapper;

	private final DeviceIdentifierService deviceIdentifierService;

	public RtaDecisionResponse decide(RtaDecisionRequest request, String clientId) {
		if (!"1.1".equals(request.getSchemaVersion()))
			throw new IllegalArgumentException("仅支持 schemaVersion=1.1");
		DeviceIdentifierService.ResolvedDevice device = deviceIdentifierService.resolve(request.getDevice(), clientId);
		RtaConfigurationCache.Configuration configuration = configurationCache.resolve(request.getTaskCode());
		var task = configuration.task();
		RtaEndpoint endpoint = configuration.endpoint();
		RtaDecisionLogBuffer.Reservation reservation = logBuffer.reserve(task.getId(), request.getRequestId());
		if (reservation.status() != RtaDecisionLogBuffer.Status.NEW)
			return new RtaDecisionResponse(request.getRequestId(), reservation.result().decision(),
					reservation.result().reasonCode(), true);
		RtaDecisionLog log = new RtaDecisionLog();
		log.setTaskId(task.getId());
		log.setEndpointId(endpoint.getId());
		log.setRequestId(request.getRequestId());
		log.setRequestHash(SignUtils.sha256(canonicalPayload(request)));
		log.setDecision("DENY");
		log.setStatus("RUNNING");
		log.setRequestedAt(LocalDateTime.now());
		long start = System.nanoTime();
		try {
			String responseBody = transport.decide(endpoint.getEndpointUrl(),
					downstreamPayload(request, endpoint, device.deviceKeyHash()));
			String decision = parseDecision(responseBody);
			log.setDecision(decision);
			log.setStatus("SUCCEEDED");
			log.setReasonCode("UPSTREAM_" + decision);
		}
		catch (RtaTransportException exception) {
			log.setDecision("DENY");
			log.setStatus("FAILED");
			log.setReasonCode(exception.code());
		}
		catch (Exception exception) {
			log.setDecision("DENY");
			log.setStatus("FAILED");
			log.setReasonCode("RTA_PROCESSING_ERROR");
		}
		log.setLatencyMs((int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000));
		log.setCompletedAt(LocalDateTime.now());
		logBuffer.complete(task.getId(), request.getRequestId(),
				new RtaDecisionLogBuffer.Result(log.getDecision(), log.getReasonCode()), log);
		return new RtaDecisionResponse(request.getRequestId(), log.getDecision(), log.getReasonCode(), false);
	}

	public String canonicalPayload(RtaDecisionRequest request) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("schemaVersion", request.getSchemaVersion());
		values.put("requestId", request.getRequestId());
		values.put("taskCode", request.getTaskCode());
		values.put("device", request.getDevice());
		values.put("attributes", request.getAttributes() == null ? Map.of() : new TreeMap<>(request.getAttributes()));
		try {
			return objectMapper.writeValueAsString(values);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("RTA 请求载荷无效");
		}
	}

	private String downstreamPayload(RtaDecisionRequest request, RtaEndpoint endpoint, String deviceKeyHash) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("rtaIdentifier", endpoint.getRtaIdentifier());
		values.put("requestId", request.getRequestId());
		values.put("taskCode", request.getTaskCode());
		values.put("deviceKeyHash", deviceKeyHash);
		values.put("attributes", request.getAttributes() == null ? Map.of() : request.getAttributes());
		try {
			return objectMapper.writeValueAsString(values);
		}
		catch (JsonProcessingException exception) {
			throw new RtaTransportException("PAYLOAD_ERROR", "RTA 下游载荷生成失败");
		}
	}

	private String parseDecision(String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
			String decision = root.path("decision").asText("DENY").trim().toUpperCase();
			return "ALLOW".equals(decision) ? "ALLOW" : "DENY";
		}
		catch (Exception exception) {
			return "DENY";
		}
	}

}
