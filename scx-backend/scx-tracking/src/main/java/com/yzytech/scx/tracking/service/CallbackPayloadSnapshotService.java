package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.delivery.entity.LinkEventMapping;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.tracking.dto.CallbackPayloadContext;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CallbackPayloadSnapshotService {

	private final CallbackJobMapper callbackMapper;

	private final LinkEventMappingMapper mappingMapper;

	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Snapshot snapshot(String callbackJobId) {
		CallbackJob job = callbackMapper.selectByIdForUpdate(callbackJobId);
		if (job == null)
			throw new CallbackDeliveryException(false, "JOB_MISSING", "回传任务不存在");
		CallbackPayloadContext context = callbackMapper.selectPayloadContext(job.getId());
		if (context == null || context.getInternalEvent() == null || context.getLinkId() == null
				|| context.getLinkConfigVersion() == null) {
			throw new CallbackDeliveryException(false, "PAYLOAD_CONTEXT_MISSING", "回传上下文不存在");
		}
		if (job.getPayloadJson() == null) {
			job.setPayloadJson(build(job, context));
			job.setPayloadHash(SignUtils.sha256(job.getPayloadJson()));
			callbackMapper.updateById(job);
		}
		return new Snapshot(job.getPayloadJson(), job.getPayloadHash(), context.getClientId());
	}

	private String build(CallbackJob job, CallbackPayloadContext context) {
		LinkEventMapping mapping = mappingMapper.selectOne(Wrappers.<LinkEventMapping>lambdaQuery()
			.eq(LinkEventMapping::getLinkId, context.getLinkId())
			.eq(LinkEventMapping::getConfigVersion, context.getLinkConfigVersion())
			.eq(LinkEventMapping::getInternalEvent, context.getInternalEvent())
			.last("LIMIT 1"));
		String externalEvent = mapping == null ? context.getInternalEvent() : mapping.getExternalEvent();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("callbackJobId", job.getId());
		payload.put("attributionResultId", job.getAttributionResultId());
		payload.put("actionType", job.getActionType());
		payload.put("taskCode", context.getTaskCode());
		payload.put("internalEvent", context.getInternalEvent());
		payload.put("eventType", externalEvent);
		payload.put("occurredAt", context.getOccurredAt().toString());
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException exception) {
			throw new CallbackDeliveryException(false, "PAYLOAD_ERROR", "回传载荷生成失败");
		}
	}

	public record Snapshot(String payload, String payloadHash, String clientId) {
	}

}
