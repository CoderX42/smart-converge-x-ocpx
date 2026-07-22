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
import com.yzytech.scx.tracking.entity.AttributionResult;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.ClickCallbackTarget;
import com.yzytech.scx.tracking.entity.ClickDedupGuard;
import com.yzytech.scx.tracking.entity.ClickDedupResult;
import com.yzytech.scx.tracking.entity.ConversionDedupResult;
import com.yzytech.scx.tracking.entity.ConversionIdentity;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.AttributionResultMapper;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.ClickCallbackTargetMapper;
import com.yzytech.scx.tracking.mapper.ClickDedupGuardMapper;
import com.yzytech.scx.tracking.mapper.ClickDedupResultMapper;
import com.yzytech.scx.tracking.mapper.ConversionDedupResultMapper;
import com.yzytech.scx.tracking.mapper.ConversionIdentityMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EventProcessingService {

	private final TrackingProperties properties;

	private final ObjectMapper objectMapper;

	private final TrackingTaskMapper taskMapper;

	private final LinkEventMappingMapper linkEventMappingMapper;

	private final RawEventMapper rawEventMapper;

	private final ClickDedupGuardMapper clickGuardMapper;

	private final ClickDedupResultMapper clickDedupMapper;

	private final ConversionIdentityMapper identityMapper;

	private final ConversionDedupResultMapper conversionDedupMapper;

	private final AttributionResultMapper attributionMapper;

	private final EventOutboxMapper outboxMapper;

	private final CallbackJobMapper callbackMapper;

	private final ClickCallbackTargetMapper callbackTargetMapper;

	@Transactional
	public void process(String rawEventId) {
		RawEvent raw = rawEventMapper.selectByIdForUpdate(rawEventId);
		if (raw == null || "PROCESSED".equals(raw.getProcessingStatus()))
			return;
		TrackingTask task = taskMapper.selectById(raw.getTaskId());
		if (task == null)
			throw new IllegalStateException("事件对应监测任务不存在");
		if ("CLICK".equals(raw.getEventType()))
			processClick(raw, task);
		else
			processConversion(raw, task);
		raw.setProcessingStatus("PROCESSED");
		raw.setProcessedAt(LocalDateTime.now());
		rawEventMapper.updateById(raw);
	}

	private void processClick(RawEvent raw, TrackingTask task) {
		ClickDedupResult result = new ClickDedupResult();
		result.setClickEventId(raw.getId());
		result.setRuleVersion(properties.getAttributionRuleVersion());
		result.setProcessedAt(LocalDateTime.now());
		if (task.getDedupWindowSec() == null || task.getDedupWindowSec() <= 0) {
			eligible(result, raw);
		}
		else {
			String guardKey = SignUtils.sha256(task.getId() + "|" + raw.getDeviceIdType() + "|" + raw.getDeviceKeyHash()
					+ "|" + properties.getAttributionRuleVersion());
			ensureGuard(guardKey, raw, task);
			ClickDedupGuard guard = clickGuardMapper.selectForUpdate(guardKey);
			if (guard.getWindowStartedAt() == null
					|| raw.getReceivedAt().isAfter(guard.getWindowStartedAt().plusSeconds(task.getDedupWindowSec()))) {
				eligible(result, raw);
				guard.setCanonicalClickEventId(raw.getId());
				guard.setWindowStartedAt(raw.getReceivedAt());
				guard.setUpdateTime(LocalDateTime.now());
				clickGuardMapper.updateById(guard);
			}
			else {
				result.setStatus("DEDUPLICATED");
				result.setReasonCode("DEVICE_WINDOW_DUPLICATE");
				result.setCanonicalClickEventId(guard.getCanonicalClickEventId());
			}
		}
		clickDedupMapper.insert(result);
		addOutbox(raw.getId(), "CLICK_CLASSIFIED",
				toJson(Map.of("status", result.getStatus(), "reasonCode", result.getReasonCode())));
	}

	private void ensureGuard(String guardKey, RawEvent raw, TrackingTask task) {
		ClickDedupGuard guard = new ClickDedupGuard();
		guard.setGuardKey(guardKey);
		guard.setTaskId(task.getId());
		guard.setDeviceIdType(raw.getDeviceIdType());
		guard.setDeviceKeyHash(raw.getDeviceKeyHash());
		guard.setRuleVersion(properties.getAttributionRuleVersion());
		try {
			clickGuardMapper.insert(guard);
		}
		catch (DuplicateKeyException ignored) {
			// The existing row is locked immediately below.
		}
	}

	private void eligible(ClickDedupResult result, RawEvent raw) {
		result.setStatus("ELIGIBLE");
		result.setReasonCode("FIRST_IN_WINDOW");
		result.setCanonicalClickEventId(raw.getId());
	}

	private void processConversion(RawEvent raw, TrackingTask task) {
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
		catch (DuplicateKeyException exception) {
			ConversionIdentity existing = identityMapper.selectOne(Wrappers.<ConversionIdentity>lambdaQuery()
				.eq(ConversionIdentity::getSource, raw.getSource())
				.eq(ConversionIdentity::getTaskId, raw.getTaskId())
				.eq(ConversionIdentity::getEventType, raw.getEventType())
				.eq(ConversionIdentity::getBusinessEventKeyHash, raw.getBusinessEventKeyHash())
				.last("LIMIT 1"));
			addConversionDedup(raw.getId(), "DUPLICATE", "BUSINESS_KEY_DUPLICATE", existing.getCanonicalEventId());
			return;
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
			attribution.setOverLimit(exceedsTaskLimit(task, raw) || exceedsEventLimit(task, raw));
		}
		attributionMapper.insert(attribution);
		addOutbox(attribution.getId(), "ATTRIBUTION_COMPLETED",
				toJson(Map.of("status", attribution.getStatus(), "reasonCode", attribution.getReasonCode())));
		if (click != null && !Boolean.TRUE.equals(attribution.getOverLimit())) {
			ClickCallbackTarget target = callbackTargetMapper.selectById(click.getId());
			CallbackJob job = new CallbackJob();
			job.setAttributionResultId(attribution.getId());
			job.setActionType("ORIGINAL");
			job.setTargetRef(target != null && StringUtils.hasText(target.getTargetRef()) ? target.getTargetRef()
					: target != null && StringUtils.hasText(target.getEncryptedUrl()) ? "dynamic." + click.getId()
							: task.getCallbackTargetRef());
			job.setStatus("PENDING");
			job.setAttempts(0);
			callbackMapper.insert(job);
		}
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

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("领域事件载荷无法序列化", exception);
		}
	}

}
