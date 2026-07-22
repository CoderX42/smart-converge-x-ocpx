package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.dto.EventReceiptView;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

	private static final Set<String> CONVERSIONS = Set.of("ACTIVATE", "REGISTER", "PAY", "ORDER", "NEXT_DAY_RETURN",
			"RETAIN_3D", "RETAIN_7D", "FIRST_ACTIVATE", "FIRST_ORDER", "REACTIVATE", "CUSTOM", "CREDIT");

	private final ObjectMapper objectMapper;

	private final TrackingProperties properties;

	private final TrackingTaskMapper taskMapper;

	private final RawEventMapper rawEventMapper;

	private final ChannelRequestAuthenticator authenticator;

	private final DeviceIdentifierService deviceIdentifierService;

	private final ChannelCredentialCrypto crypto;

	private final FeedbackBuffer feedbackBuffer;

	private final AcceptedEventPersistenceService persistenceService;

	public EventIngestionResponse accept(String clientId, String keyVersion, String timestamp, String nonce,
			String sign, String path, String forcedEventType, EventIngestionRequest request) {
		if (!properties.isIngressEnabled())
			throw new ChannelAuthenticationException(503, "渠道接入服务未启用");
		String eventType = normalizeAndValidate(request, forcedEventType);
		String canonicalPayload = canonicalPayload(request, eventType);
		ChannelAuthContext auth = authenticator.authenticate(clientId, keyVersion, timestamp, nonce, sign, "POST", path,
				canonicalPayload);
		TrackingTask task = requireTask(request.getTaskCode(), eventType);
		DeviceIdentifierService.ResolvedDevice device = deviceIdentifierService.resolve(request.getDevice(), clientId);
		OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
		String receiptId = UUID.randomUUID().toString();
		String payloadHash = SignUtils.sha256(canonicalPayload);
		CallbackTarget callback = callbackTarget(auth.client(), request, eventType);
		AcceptedEventCommand command = new AcceptedEventCommand(receiptId, clientId, auth.client().getSourceCode(),
				request.getEventId(), task.getCode(), eventType, request.getOccurredAt(), receivedAt, device.platform(),
				device.idType(), device.idVersion(), device.keyVersion(), device.deviceKeyHash(),
				StringUtils.hasText(request.getBusinessEventKey()) ? SignUtils.sha256(request.getBusinessEventKey())
						: null,
				payloadHash, canonicalPayload, callback.reference(), callback.ciphertext(), callback.iv(),
				callback.host(), callback.fingerprint());

		if ("CLICK".equals(eventType)) {
			FeedbackBuffer.EnqueueResult result = feedbackBuffer.enqueue(command);
			if (result.status() == FeedbackBuffer.Status.CONFLICT)
				throw new EventConflictException("eventId 已被不同载荷占用");
			return new EventIngestionResponse(result.receiptId(), request.getEventId(),
					result.status() == FeedbackBuffer.Status.DUPLICATE,
					result.status() == FeedbackBuffer.Status.DUPLICATE ? "ACCEPTED" : "BUFFERED", receivedAt);
		}

		AcceptedEventPersistenceService.PersistenceResult result = persistenceService.persist(command);
		return new EventIngestionResponse(result.receiptId(), request.getEventId(), result.duplicateDelivery(),
				"ACCEPTED", result.receivedAt());
	}

	public EventReceiptView receipt(String clientId, String keyVersion, String timestamp, String nonce, String sign,
			String path, String receiptId) {
		if (!properties.isIngressEnabled())
			throw new ChannelAuthenticationException(503, "渠道接入服务未启用");
		authenticator.authenticate(clientId, keyVersion, timestamp, nonce, sign, "GET", path, "");
		RawEvent raw = rawEventMapper.selectOne(Wrappers.<RawEvent>lambdaQuery()
			.eq(RawEvent::getClientId, clientId)
			.eq(RawEvent::getReceiptId, receiptId)
			.last("LIMIT 1"));
		if (raw == null)
			return null;
		return new EventReceiptView(raw.getReceiptId(), raw.getEventId(), false, raw.getStatus(),
				raw.getReceivedAt().atOffset(ZoneOffset.UTC), raw.getProcessingStatus(), null, null);
	}

	public String canonicalPayload(EventIngestionRequest request) {
		return canonicalPayload(request, request.getEventType());
	}

	public String canonicalPayload(EventIngestionRequest request, String eventType) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("schemaVersion", request.getSchemaVersion());
		value.put("eventId", request.getEventId());
		value.put("taskCode", request.getTaskCode());
		value.put("eventType", upper(eventType));
		value.put("occurredAt", request.getOccurredAt() == null ? null : request.getOccurredAt().toString());
		if (request.getDevice() != null) {
			Map<String, Object> device = new LinkedHashMap<>();
			device.put("platform", upper(request.getDevice().getPlatform()));
			device.put("idType", upper(request.getDevice().getIdType()));
			device.put("hashAlgorithm", upper(request.getDevice().getHashAlgorithm()));
			device.put("idHash", request.getDevice().getIdHash());
			device.put("idVersion", request.getDevice().getIdVersion());
			value.put("device", device);
		}
		value.put("businessEventKey", request.getBusinessEventKey());
		value.put("callbackRef", request.getCallbackRef());
		value.put("callbackUrl", request.getCallbackUrl());
		value.put("attributes", request.getAttributes() == null ? Map.of() : new TreeMap<>(request.getAttributes()));
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("事件载荷无法序列化");
		}
	}

	private String normalizeAndValidate(EventIngestionRequest request, String forcedEventType) {
		if (!"1.1".equals(request.getSchemaVersion()))
			throw new IllegalArgumentException("仅支持 schemaVersion=1.1");
		String eventType = forcedEventType == null ? upper(request.getEventType()) : forcedEventType;
		if (!"CLICK".equals(eventType) && !CONVERSIONS.contains(eventType))
			throw new IllegalArgumentException("不支持的 eventType");
		request.setEventType(eventType);
		if (CONVERSIONS.contains(eventType) && !StringUtils.hasText(request.getBusinessEventKey()))
			throw new IllegalArgumentException("转化事件必须携带 businessEventKey");
		return eventType;
	}

	private TrackingTask requireTask(String taskCode, String eventType) {
		TrackingTask task = taskMapper
			.selectOne(Wrappers.<TrackingTask>lambdaQuery().eq(TrackingTask::getCode, taskCode).last("LIMIT 1"));
		if (task == null || !"ACTIVE".equals(task.getStatus()))
			throw new IllegalArgumentException("监测任务不可用");
		if ("CLICK".equals(eventType) && task.getDedupWindowSec() == null)
			throw new IllegalArgumentException("监测任务未配置点击验重窗口");
		return task;
	}

	private CallbackTarget callbackTarget(ChannelClient client, EventIngestionRequest request, String eventType) {
		if (!"CLICK".equals(eventType))
			return CallbackTarget.empty();
		if (StringUtils.hasText(request.getCallbackRef()) && StringUtils.hasText(request.getCallbackUrl()))
			throw new IllegalArgumentException("callbackRef 与 callbackUrl 只能选择一个");
		if (StringUtils.hasText(request.getCallbackRef())) {
			if (!request.getCallbackRef().matches("[A-Za-z0-9._-]{1,128}"))
				throw new IllegalArgumentException("callbackRef 格式无效");
			return new CallbackTarget(request.getCallbackRef(), null, null, null, null);
		}
		if (!StringUtils.hasText(request.getCallbackUrl()))
			return CallbackTarget.empty();
		if (!Boolean.TRUE.equals(client.getDynamicCallbackEnabled()))
			throw new IllegalArgumentException("当前渠道未启用动态 callbackUrl");
		URI uri;
		try {
			uri = URI.create(request.getCallbackUrl());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("callbackUrl 格式无效");
		}
		String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
		Set<String> allowed = Arrays
			.stream(StringUtils.hasText(client.getCallbackAllowedHosts()) ? client.getCallbackAllowedHosts().split(",")
					: new String[0])
			.map(String::trim)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());
		if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || host == null || !allowed.contains(host))
			throw new IllegalArgumentException("callbackUrl 不在渠道允许域名列表");
		ChannelCredentialCrypto.EncryptedValue encrypted = crypto.encrypt(request.getCallbackUrl());
		return new CallbackTarget(null, encrypted.ciphertext(), encrypted.iv(), host,
				SignUtils.sha256(request.getCallbackUrl()));
	}

	private String upper(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private record CallbackTarget(String reference, String ciphertext, String iv, String host, String fingerprint) {

		private static CallbackTarget empty() {
			return new CallbackTarget(null, null, null, null, null);
		}

	}

}
