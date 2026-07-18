package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.delivery.entity.LinkEventMapping;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.entity.*;
import com.yzytech.scx.tracking.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

	private static final Set<String> CONVERSIONS = Set.of("ACTIVATE", "REGISTER", "PAY", "ORDER", "NEXT_DAY_RETURN",
			"RETAIN_3D", "RETAIN_7D", "FIRST_ACTIVATE", "FIRST_ORDER", "REACTIVATE", "CUSTOM", "CREDIT");

	private static final Set<String> ALLOWED = Set.of("CLICK", "IMPRESSION", "ACTIVATE", "REGISTER", "PAY", "ORDER",
			"NEXT_DAY_RETURN", "RETAIN_3D", "RETAIN_7D", "FIRST_ACTIVATE", "FIRST_ORDER", "REACTIVATE", "CUSTOM",
			"CREDIT");

	private final TrackingProperties properties;

	private final ObjectMapper objectMapper;

	private final TrackingTaskMapper taskMapper;

	private final LinkEventMappingMapper linkEventMappingMapper;

	private final RawEventMapper rawEventMapper;

	private final ClickDedupResultMapper clickDedupMapper;

	private final ConversionIdentityMapper identityMapper;

	private final ConversionDedupResultMapper conversionDedupMapper;

	private final AttributionResultMapper attributionMapper;

	private final EventOutboxMapper outboxMapper;

	private final CallbackJobMapper callbackMapper;

	public String canonicalPayload(EventIngestionRequest request) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("schemaVersion", request.getSchemaVersion());
		value.put("eventId", request.getEventId());
		value.put("source", request.getSource());
		value.put("taskCode", request.getTaskCode());
		value.put("eventType", request.getEventType().toUpperCase());
		value.put("occurredAt", request.getOccurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		value.put("deviceKeyHash", request.getDeviceKeyHash());
		value.put("businessEventKeyHash", StringUtils.hasText(request.getBusinessEventKey())
				? SignUtils.sha256(request.getBusinessEventKey()) : null);
		value.put("attributes", request.getAttributes() == null ? Map.of() : new TreeMap<>(request.getAttributes()));
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("事件载荷无法序列化");
		}
	}

	@Transactional
	public EventIngestionResponse ingest(EventIngestionRequest request) {
		if (!"1.0".equals(request.getSchemaVersion()))
			throw new IllegalArgumentException("不支持的 schema_version");
		String eventType = request.getEventType().toUpperCase();
		if (!ALLOWED.contains(eventType))
			throw new IllegalArgumentException("不支持的 event_type");
		if (!request.getDeviceKeyHash().matches("(?i)[0-9a-f]{64}"))
			throw new IllegalArgumentException("device_key_hash 必须是 SHA-256 十六进制值");
		if (CONVERSIONS.contains(eventType) && !StringUtils.hasText(request.getBusinessEventKey()))
			throw new IllegalArgumentException("转化事件必须携带 business_event_key");
		TrackingTask task = taskMapper.selectOne(
				Wrappers.<TrackingTask>lambdaQuery().eq(TrackingTask::getCode, request.getTaskCode()).last("LIMIT 1"));
		if (task == null || !"ACTIVE".equals(task.getStatus()))
			throw new IllegalArgumentException("监测任务不可用");
		if ("IMPRESSION".equals(eventType) && !Boolean.TRUE.equals(task.getImpressionEnabled())) {
			throw new IllegalArgumentException("监测任务未开启曝光上报");
		}

		String payload = canonicalPayload(request);
		String payloadHash = SignUtils.sha256(payload);
		RawEvent existing = rawEventMapper.selectOne(Wrappers.<RawEvent>lambdaQuery()
			.eq(RawEvent::getSource, request.getSource())
			.eq(RawEvent::getEventId, request.getEventId())
			.last("LIMIT 1"));
		if (existing != null)
			return duplicateResponse(existing, payloadHash);

		RawEvent raw = new RawEvent();
		raw.setSource(request.getSource());
		raw.setEventId(request.getEventId());
		raw.setTaskId(task.getId());
		raw.setEventType(eventType);
		raw.setOccurredAt(request.getOccurredAt());
		raw.setReceivedAt(LocalDateTime.now());
		raw.setDeviceKeyHash(request.getDeviceKeyHash().toLowerCase());
		raw.setPayloadJson(payload);
		raw.setPayloadHash(payloadHash);
		raw.setStatus("ACCEPTED");
		if (CONVERSIONS.contains(eventType))
			raw.setBusinessEventKeyHash(SignUtils.sha256(request.getBusinessEventKey()));
		try {
			rawEventMapper.insert(raw);
		}
		catch (DuplicateKeyException e) {
			existing = rawEventMapper.selectOne(Wrappers.<RawEvent>lambdaQuery()
				.eq(RawEvent::getSource, request.getSource())
				.eq(RawEvent::getEventId, request.getEventId())
				.last("LIMIT 1"));
			return duplicateResponse(existing, payloadHash);
		}

		addOutbox(raw.getId(), "RAW_EVENT_INGESTED", payload);
		if ("CLICK".equals(eventType))
			return processClick(raw, task);
		if ("IMPRESSION".equals(eventType))
			return processImpression(raw);
		return processConversion(raw, task);
	}

	private EventIngestionResponse processClick(RawEvent raw, TrackingTask task) {
		RawEvent canonical = task.getDedupWindowSec() != null && task.getDedupWindowSec() > 0
				? rawEventMapper.findLastEligibleClick(task.getId(), raw.getDeviceKeyHash(),
						raw.getOccurredAt().minusSeconds(task.getDedupWindowSec()), raw.getOccurredAt())
				: null;
		ClickDedupResult result = new ClickDedupResult();
		result.setClickEventId(raw.getId());
		result.setRuleVersion(properties.getAttributionRuleVersion());
		result.setProcessedAt(LocalDateTime.now());
		if (canonical == null) {
			result.setStatus("ELIGIBLE");
			result.setReasonCode("FIRST_IN_WINDOW");
			result.setCanonicalClickEventId(raw.getId());
		}
		else {
			result.setStatus("DEDUPLICATED");
			result.setReasonCode("DEVICE_WINDOW_DUPLICATE");
			result.setCanonicalClickEventId(canonical.getId());
		}
		clickDedupMapper.insert(result);
		addOutbox(raw.getId(), "CLICK_CLASSIFIED",
				toJson(Map.of("status", result.getStatus(), "reasonCode", result.getReasonCode())));
		return new EventIngestionResponse(raw.getId(), false, result.getStatus(), result.getReasonCode(), null);
	}

	private EventIngestionResponse processImpression(RawEvent raw) {
		addOutbox(raw.getId(), "IMPRESSION_INGESTED", toJson(Map.of("status", "ACCEPTED")));
		return new EventIngestionResponse(raw.getId(), false, "ACCEPTED", "IMPRESSION_RECORDED", null);
	}

	private EventIngestionResponse processConversion(RawEvent raw, TrackingTask task) {
		ConversionIdentity identity = new ConversionIdentity();
		identity.setSource(raw.getSource());
		identity.setTaskId(raw.getTaskId());
		identity.setEventType(raw.getEventType());
		identity.setBusinessEventKeyHash(raw.getBusinessEventKeyHash());
		identity.setCanonicalEventId(raw.getId());
		identity.setCreateTime(LocalDateTime.now());
		try {
			identityMapper.insert(identity);
		}
		catch (DuplicateKeyException e) {
			ConversionIdentity existing = identityMapper.selectOne(Wrappers.<ConversionIdentity>lambdaQuery()
				.eq(ConversionIdentity::getSource, raw.getSource())
				.eq(ConversionIdentity::getTaskId, raw.getTaskId())
				.eq(ConversionIdentity::getEventType, raw.getEventType())
				.eq(ConversionIdentity::getBusinessEventKeyHash, raw.getBusinessEventKeyHash())
				.last("LIMIT 1"));
			addConversionDedup(raw.getId(), "DUPLICATE", "BUSINESS_KEY_DUPLICATE", existing.getCanonicalEventId());
			return new EventIngestionResponse(raw.getId(), false, "DUPLICATE", "BUSINESS_KEY_DUPLICATE", null);
		}
		addConversionDedup(raw.getId(), "CANONICAL", "FIRST_BUSINESS_EVENT", raw.getId());
		RawEvent click = rawEventMapper.findLastEligibleClick(task.getId(), raw.getDeviceKeyHash(),
				raw.getOccurredAt().minusHours(task.getAttributionWindowHours()), raw.getOccurredAt());
		AttributionResult attribution = new AttributionResult();
		attribution.setConversionEventId(raw.getId());
		attribution.setRevision(1);
		attribution.setRuleVersion(properties.getAttributionRuleVersion());
		attribution.setCurrentFlag(true);
		attribution.setAttributedAt(LocalDateTime.now());
		attribution.setReviewStatus("PENDING");
		if (click == null) {
			attribution.setStatus("UNATTRIBUTED");
			attribution.setReasonCode("NO_ELIGIBLE_CLICK");
		}
		else {
			attribution.setClickEventId(click.getId());
			attribution.setStatus("ATTRIBUTED");
			attribution.setReasonCode("LAST_CLICK_MATCHED");
			boolean overLimit = exceedsTaskLimit(task, raw) || exceedsEventLimit(task, raw);
			attribution.setOverLimit(overLimit);
		}
		attributionMapper.insert(attribution);
		addOutbox(attribution.getId(), "ATTRIBUTION_COMPLETED",
				toJson(Map.of("status", attribution.getStatus(), "reasonCode", attribution.getReasonCode())));
		if (click != null && !Boolean.TRUE.equals(attribution.getOverLimit())) {
			CallbackJob job = new CallbackJob();
			job.setAttributionResultId(attribution.getId());
			job.setActionType("ORIGINAL");
			job.setTargetRef(task.getCallbackTargetRef());
			job.setStatus("PENDING");
			job.setAttempts(0);
			callbackMapper.insert(job);
		}
		return new EventIngestionResponse(raw.getId(), false, "CANONICAL", "FIRST_BUSINESS_EVENT",
				attribution.getStatus());
	}

	private boolean exceedsTaskLimit(TrackingTask task, RawEvent raw) {
		return task.getDailyConversionLimit() != null && task.getDailyConversionLimit() > 0
				&& attributionMapper.countSettlementEligibleForTaskDate(task.getId(),
						raw.getOccurredAt().toLocalDate()) >= task.getDailyConversionLimit();
	}

	private boolean exceedsEventLimit(TrackingTask task, RawEvent raw) {
		LinkEventMapping mapping = linkEventMappingMapper.selectOne(Wrappers.<LinkEventMapping>lambdaQuery()
			.eq(LinkEventMapping::getLinkId, task.getLinkId())
			.eq(LinkEventMapping::getInternalEvent, raw.getEventType())
			.orderByDesc(LinkEventMapping::getConfigVersion)
			.last("LIMIT 1"));
		return mapping != null && mapping.getDailyLimit() != null && mapping.getDailyLimit() > 0
				&& attributionMapper.countSettlementEligibleForTaskEventDate(task.getId(), raw.getEventType(),
						raw.getOccurredAt().toLocalDate()) >= mapping.getDailyLimit();
	}

	private void addConversionDedup(String id, String status, String reason, String canonical) {
		ConversionDedupResult result = new ConversionDedupResult();
		result.setConversionEventId(id);
		result.setStatus(status);
		result.setReasonCode(reason);
		result.setCanonicalEventId(canonical);
		result.setProcessedAt(LocalDateTime.now());
		conversionDedupMapper.insert(result);
	}

	private void addOutbox(String aggregateId, String eventType, String payload) {
		EventOutbox value = new EventOutbox();
		value.setAggregateId(aggregateId);
		value.setEventType(eventType);
		value.setPayloadJson(payload);
		value.setStatus("PENDING");
		value.setAttempts(0);
		outboxMapper.insert(value);
	}

	private EventIngestionResponse duplicateResponse(RawEvent existing, String payloadHash) {
		if (existing == null || !payloadHash.equals(existing.getPayloadHash())) {
			throw new IllegalArgumentException("event_id 已被不同载荷占用");
		}
		return new EventIngestionResponse(existing.getId(), true, "ACCEPTED", "DUPLICATE_DELIVERY", null);
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			return "{}";
		}
	}

}
