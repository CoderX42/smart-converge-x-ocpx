package com.yzytech.scx.ocpx.audit.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import com.yzytech.scx.ocpx.audit.store.WireAuditStore;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WireAuditService {

	private final WireAuditProperties properties;
	private final OcpxKeyRingCrypto crypto;
	private final WireAuditStore store;

	public boolean enabled() {
		return properties.isEnabled();
	}

	public Handle beginInbound(String stage, String protocol, String trafficClass, Map<String, Object> request,
			Route route) {
		return begin(stage, "INBOUND", protocol, trafficClass, true, request, route);
	}

	public Handle beginOutbound(String stage, String protocol, String trafficClass, String url, boolean wireSent,
			FeedbackReq feedback) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("method", "GET");
		putUrl(request, url);
		request.put("headers", Map.of());
		request.put("bodyBase64", "");
		return begin(stage, "OUTBOUND", protocol, trafficClass, wireSent, request, Route.from(feedback));
	}

	public void completeHttp(Handle handle, Integer status, Map<String, List<String>> headers, byte[] responseBody,
			String errorCode, Throwable exception, Route enrichedRoute) {
		if (handle == null)
			return;
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", status);
		response.put("headers", headers == null ? Map.of() : headers);
		response.put("bodyBase64",
				Base64.getEncoder().encodeToString(responseBody == null ? new byte[0] : responseBody));
		if (exception != null) {
			response.put("exceptionType", exception.getClass().getName());
			response.put("exceptionMessage", exception.getMessage());
		}
		complete(handle, response, errorCode, status != null && status >= 200 && status < 400 && exception == null,
				enrichedRoute);
	}

	public void completeOutbound(Handle handle, NetUtil.HttpGetResult result, String errorCode) {
		if (handle == null)
			return;
		if (result == null) {
			completeHttp(handle, null, Map.of(), new byte[0], errorCode, null, null);
			return;
		}
		String conclusion = StringUtils.hasText(result.errorCode()) ? result.errorCode() : errorCode;
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", result.statusCode());
		response.put("headers", result.headers());
		response.put("bodyBase64", Base64.getEncoder().encodeToString(
				result.rawBody() == null ? bytes(result.body()) : result.rawBody()));
		if (StringUtils.hasText(result.exceptionType())) {
			response.put("exceptionType", result.exceptionType());
			response.put("exceptionMessage", result.exceptionMessage());
		}
		boolean success = result.statusCode() != null && result.statusCode() >= 200 && result.statusCode() < 400
				&& !failureConclusion(conclusion);
		complete(handle, response, conclusion, success, null);
	}

	private boolean failureConclusion(String value) {
		if (!StringUtils.hasText(value))
			return false;
		String normalized = value.toUpperCase(java.util.Locale.ROOT);
		return normalized.contains("FAILED") || normalized.contains("ERROR") || normalized.contains("REJECTED")
				|| normalized.contains("LIMIT") || normalized.contains("MISSING") || normalized.contains("TIMEOUT");
	}

	public void internal(CallbackReq callback, String stage, Object input, Object output, boolean success,
			String errorCode, Throwable exception) {
		if (!enabled())
			return;
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("input", input);
		Handle handle = begin(stage, "INTERNAL", "SCX", traffic(callback), false, request, Route.from(callback));
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("output", output);
		if (exception != null) {
			response.put("exceptionType", exception.getClass().getName());
			response.put("exceptionMessage", exception.getMessage());
		}
		complete(handle, response, errorCode, success, Route.from(callback));
	}

	private Handle begin(String stage, String direction, String protocol, String trafficClass, boolean wireSent,
			Map<String, Object> request, Route route) {
		if (!enabled())
			return null;
		LocalDateTime capturedAt = LocalDateTime.now();
		String id = UUID.randomUUID().toString().replace("-", "");
		String aad = aad(id, stage, direction, capturedAt);
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("request", request);
		envelope.put("response", null);
		byte[] requestBytes = JSON.toJSONBytes(request);
		WireAuditRecord record = base(id, stage, direction, protocol, trafficClass, wireSent, route, request, capturedAt)
			.resultStatus("PENDING")
			.rawState("PENDING")
			.requestBytes(requestBytes.length)
			.requestSha256(hash(requestBytes))
			.build();
		seal(record, envelope, aad);
		store.persist(record);
		return new Handle(record, request, capturedAt);
	}

	private void complete(Handle handle, Map<String, Object> response, String errorCode, boolean success, Route route) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("request", handle.request());
		envelope.put("response", response);
		byte[] responseBytes = JSON.toJSONBytes(response);
		byte[] requestBytes = JSON.toJSONBytes(handle.request());
		Route merged = Route.merge(handle.record(), route);
		WireAuditRecord completed = handle.record()
			.toBuilder()
			.traceId(merged.traceId())
			.requestId(merged.requestId())
			.advertiserId(merged.advertiserId())
			.promotionLinkId(merged.promotionLinkId())
			.mediaId(merged.mediaId())
			.monitorTaskId(merged.monitorTaskId())
			.resultStatus(success ? "SUCCESS" : "FAILED")
			.partnerHttpStatus(number(response.get("status")))
			.errorCode(limit(errorCode, 128))
			.requestBytes(requestBytes.length)
			.requestSha256(hash(requestBytes))
			.responseBytes(responseBytes.length)
			.responseSha256(hash(responseBytes))
			.rawState("COMPLETE")
			.finishedAt(LocalDateTime.now())
			.build();
		seal(completed, envelope, completed.getPayloadAad());
		store.persist(completed);
	}

	private WireAuditRecord.WireAuditRecordBuilder base(String id, String stage, String direction, String protocol,
			String trafficClass, boolean wireSent, Route route, Map<String, Object> request, LocalDateTime capturedAt) {
		String path = string(request.get("path"));
		String method = string(request.get("method"));
		return WireAuditRecord.builder()
			.id(id)
			.traceId(route == null ? null : route.traceId())
			.stageCode(stage)
			.direction(direction)
			.trafficClass(StringUtils.hasText(trafficClass) ? trafficClass : "FORMAL")
			.protocol(StringUtils.hasText(protocol) ? protocol : "UNKNOWN")
			.requestMethod(limit(method, 16))
			.requestPath(limit(path, 512))
			.wireSent(wireSent)
			.requestId(route == null ? null : route.requestId())
			.advertiserId(route == null ? null : route.advertiserId())
			.promotionLinkId(route == null ? null : route.promotionLinkId())
			.mediaId(route == null ? null : route.mediaId())
			.monitorTaskId(route == null ? null : route.monitorTaskId())
			.keyId(crypto.activeKeyId())
			.cryptoVersion(1)
			.captureSource("DIRECT")
			.capturedAt(capturedAt)
			.rawExpiresAt(capturedAt.plusDays(properties.getRawRetentionDays()))
			.metadataExpiresAt(capturedAt.plusDays(properties.getMetadataRetentionDays()));
	}

	private void seal(WireAuditRecord record, Map<String, Object> envelope, String aad) {
		byte[] clear = JSON.toJSONBytes(envelope);
		OcpxKeyRingCrypto.SealedValue sealed = crypto.encrypt(clear, OcpxKeyRingCrypto.PURPOSE_AUDIT,
				aad.getBytes(StandardCharsets.UTF_8));
		record.setKeyId(sealed.keyId());
		record.setCryptoVersion(sealed.version());
		record.setPayloadAad(aad);
		record.setPayloadNonce(sealed.nonce());
		record.setPayloadCiphertext(sealed.cipherText());
	}

	private String aad(String id, String stage, String direction, LocalDateTime capturedAt) {
		return id + "|" + stage + "|" + direction + "|" + capturedAt;
	}

	private String traffic(CallbackReq request) {
		String requestId = request == null || request.getFeedback() == null ? null : request.getFeedback().getReqId();
		return requestId != null && requestId.startsWith("SCX-UAT-") ? "TEST" : "FORMAL";
	}

	private void putUrl(Map<String, Object> request, String url) {
		if (!StringUtils.hasText(url)) {
			request.put("path", null);
			request.put("rawQuery", null);
			return;
		}
		try {
			URI uri = URI.create(url);
			request.put("scheme", uri.getScheme());
			request.put("authority", uri.getRawAuthority());
			request.put("path", uri.getRawPath());
			request.put("rawQuery", uri.getRawQuery());
		}
		catch (IllegalArgumentException exception) {
			request.put("path", null);
			request.put("rawTarget", url);
		}
	}

	private String hash(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to hash wire audit", exception);
		}
	}

	private byte[] bytes(String value) {
		return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
	}

	private Integer number(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private String string(Object value) {
		return value == null ? null : value.toString();
	}

	private String limit(String value, int length) {
		return value == null || value.length() <= length ? value : value.substring(0, length);
	}

	public record Handle(WireAuditRecord record, Map<String, Object> request, LocalDateTime capturedAt) {
	}

	public record Route(String traceId, String requestId, String advertiserId, String promotionLinkId, String mediaId,
			String monitorTaskId) {

		public static Route from(FeedbackReq feedback) {
			if (feedback == null)
				return null;
			return new Route(trace(feedback), feedback.getReqId(), feedback.getAId(), feedback.getLId(), feedback.getMId(),
					feedback.getTId());
		}

		public static Route from(CallbackReq callback) {
			return callback == null ? null : from(callback.getFeedback());
		}

		private static Route merge(WireAuditRecord record, Route route) {
			if (route == null) {
				return new Route(record.getTraceId(), record.getRequestId(), record.getAdvertiserId(),
						record.getPromotionLinkId(), record.getMediaId(), record.getMonitorTaskId());
			}
			return route;
		}

		private static String trace(FeedbackReq feedback) {
			try {
				String value = String.join("|", safe(feedback.getMId()), safe(feedback.getTId()), safe(feedback.getAId()),
						safe(feedback.getLId()), safe(feedback.getReqId()));
				return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
			}
			catch (Exception exception) {
				throw new IllegalStateException("unable to calculate audit trace", exception);
			}
		}

		private static String safe(String value) {
			return value == null ? "" : value;
		}
	}

}
