package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import com.yzytech.scx.tracking.mapper.RtaDecisionLogMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RtaDecisionService {

	private final TrackingTaskMapper taskMapper;

	private final RtaEndpointMapper endpointMapper;

	private final RtaDecisionLogMapper logMapper;

	private final RtaTransport transport;

	private final ObjectMapper objectMapper;

	@Transactional
	public RtaDecisionResponse decide(RtaDecisionRequest request) {
		TrackingTask task = taskMapper.selectOne(
				Wrappers.<TrackingTask>lambdaQuery().eq(TrackingTask::getCode, request.getTaskCode()).last("LIMIT 1"));
		if (task == null || !"ACTIVE".equals(task.getStatus()) || !Boolean.TRUE.equals(task.getRtaEnabled())) {
			throw new IllegalArgumentException("任务未启用 RTA 决策");
		}
		RtaEndpoint endpoint = endpointMapper.selectById(task.getRtaEndpointId());
		if (endpoint == null || !task.getMediaId().equals(endpoint.getMediaId())
				|| !"ACTIVE".equals(endpoint.getStatus())) {
			throw new IllegalArgumentException("任务绑定的 RTA 配置不可用");
		}
		RtaDecisionLog duplicate = logMapper.selectByTaskAndRequest(task.getId(), request.getRequestId());
		if (duplicate != null) {
			return new RtaDecisionResponse(request.getRequestId(), duplicate.getDecision(), duplicate.getReasonCode(),
					true);
		}
		RtaDecisionLog log = new RtaDecisionLog();
		log.setTaskId(task.getId());
		log.setEndpointId(endpoint.getId());
		log.setRequestId(request.getRequestId());
		log.setRequestHash(SignUtils.sha256(canonicalPayload(request)));
		log.setDecision("DENY");
		log.setStatus("RUNNING");
		log.setRequestedAt(LocalDateTime.now());
		logMapper.insert(log);
		long start = System.nanoTime();
		try {
			String responseBody = transport.decide(endpoint.getEndpointUrl(), downstreamPayload(request, endpoint));
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
		logMapper.updateById(log);
		return new RtaDecisionResponse(request.getRequestId(), log.getDecision(), log.getReasonCode(), false);
	}

	public String canonicalPayload(RtaDecisionRequest request) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("schemaVersion", request.getSchemaVersion());
		values.put("requestId", request.getRequestId());
		values.put("taskCode", request.getTaskCode());
		values.put("deviceKeyHash", request.getDeviceKeyHash());
		values.put("attributes", request.getAttributes() == null ? Map.of() : new TreeMap<>(request.getAttributes()));
		try {
			return objectMapper.writeValueAsString(values);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("RTA 请求载荷无效");
		}
	}

	private String downstreamPayload(RtaDecisionRequest request, RtaEndpoint endpoint) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("rtaIdentifier", endpoint.getRtaIdentifier());
		values.put("requestId", request.getRequestId());
		values.put("taskCode", request.getTaskCode());
		values.put("deviceKeyHash", request.getDeviceKeyHash());
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
