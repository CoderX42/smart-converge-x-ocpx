package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/** 将 Feedback 与 Callback 处置写入脱敏流程日志，不保存设备标识、请求 URL 或凭据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcpxFlowLogWriter {

	private static final int RESPONSE_PREVIEW_LIMIT = 4096;

	private static final String SENSITIVE_KEY = "access_token|api_key|client_secret|password|satoken|secret|signature|sign|token|key";

	private static final Pattern JSON_SECRET = Pattern
		.compile("(?i)(\\\"(?:" + SENSITIVE_KEY + ")\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");

	private static final Pattern PARAM_SECRET = Pattern
		.compile("(?i)((?:^|[?&\\s])(?:" + SENSITIVE_KEY + ")=)[^&\\s]*");

	private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/-]+=*");

	private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

	private final JdbcTemplate jdbcTemplate;

	public void rejectedAdvertiser(FeedbackReq request) {
		writeFeedback(request, false, null, "ADVERTISER_PROTOCOL_NOT_REGISTERED", 0L, null);
	}

	/** 记录出站结果；仅保留脱敏、限长的响应预览。 */
	public void upstreamDelivery(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs) {
		upstreamDelivery(request, success, httpStatus, message, durationMs, null);
	}

	public void upstreamDelivery(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs, String responseBody) {
		writeFeedback(request, success, httpStatus, message, durationMs, responsePreview(responseBody));
	}

	/**
	 * 记录 Callback 接收与处置结果。异步下游派发只能记录已提交，不能据此声明媒体已经收到。
	 * @param request 已恢复 Feedback 上下文的 Callback
	 * @param success Callback 是否被当前处理链正常处置
	 * @param message 脱敏的稳定结论码
	 */
	public void callback(CallbackReq request, boolean success, String message) {
		if (request == null || request.getFeedback() == null) {
			log.warn("Unable to persist callback audit without feedback context");
			return;
		}
		String eventCode = request.getActionType() == null ? "UNKNOWN" : request.getActionType().name();
		write(request.getFeedback(), "CALLBACK", eventCode, callbackFingerprint(request), success, null, message, null,
				0L);
	}

	private void writeFeedback(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs, String responsePreview) {
		String eventCode = request.getEvent() == null ? "UNKNOWN" : request.getEvent().getKey();
		write(request, "FEEDBACK", eventCode, feedbackFingerprint(request), success, httpStatus, message,
				responsePreview, durationMs);
	}

	private void write(FeedbackReq request, String flowType, String eventCode, String payloadHash, boolean success,
			Integer httpStatus, String message, String responsePreview, long durationMs) {
		try {
			String eventId = StringUtils.hasText(request.getReqId()) ? limit(request.getReqId(), 128)
					: UUID.randomUUID().toString();
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,response_preview,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", uuid(), flowType, eventId, null, eventCode, payloadHash, request.getAId(), request.getLId(),
					request.getMId(), request.getTId(), success ? "SUCCESS" : "FAILED", httpStatus,
					limit(safe(message), 500), responsePreview, durationMs, LocalDateTime.now());
		}
		catch (RuntimeException error) {
			log.warn("Unable to persist {} flow audit for aId:{}", flowType, request.getAId());
		}
	}

	private String responsePreview(String responseBody) {
		if (!StringUtils.hasText(responseBody)) {
			return null;
		}
		String sanitized = UNSAFE_CONTROL.matcher(responseBody).replaceAll("");
		sanitized = JSON_SECRET.matcher(sanitized).replaceAll("$1***$2");
		sanitized = PARAM_SECRET.matcher(sanitized).replaceAll("$1***");
		sanitized = BEARER_SECRET.matcher(sanitized).replaceAll("$1***");
		return limit(sanitized, RESPONSE_PREVIEW_LIMIT);
	}

	private String feedbackFingerprint(FeedbackReq request) {
		return fingerprint(String.join("|", safe(request.getAId()), safe(request.getLId()), safe(request.getMId()),
				safe(request.getTId()), request.getEvent() == null ? "" : request.getEvent().name(),
				safe(request.getReqId())));
	}

	private String callbackFingerprint(CallbackReq request) {
		FeedbackReq feedback = request.getFeedback();
		return fingerprint(String.join("|", safe(feedback.getAId()), safe(feedback.getLId()), safe(feedback.getMId()),
				safe(feedback.getTId()), request.getActionType() == null ? "" : request.getActionType().name(),
				safe(feedback.getReqId()), safe(request.getPartnerActionType()), safe(request.getActionTime()),
				safe(request.getCurrencyAmount())));
	}

	private String fingerprint(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("Unable to hash flow audit", error);
		}
	}

	private String uuid() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String safe(Object value) {
		return value == null ? "" : value.toString();
	}

	private String limit(String value, int length) {
		return value.length() <= length ? value : value.substring(0, length);
	}

}
