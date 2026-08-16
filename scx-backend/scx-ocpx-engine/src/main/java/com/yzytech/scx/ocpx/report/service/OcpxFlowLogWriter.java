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

/** 将 Feedback 与 Callback 处置写入脱敏流程日志，不保存设备标识、URL 或凭据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcpxFlowLogWriter {

	private final JdbcTemplate jdbcTemplate;

	public void rejectedAdvertiser(FeedbackReq request) {
		writeFeedback(request, false, null, "ADVERTISER_PROTOCOL_NOT_REGISTERED", 0L);
	}

	/** 记录 AERIM 出站结果，不保存请求 URL、响应正文或设备标识。 */
	public void upstreamDelivery(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs) {
		writeFeedback(request, success, httpStatus, message, durationMs);
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
		write(request.getFeedback(), "CALLBACK", eventCode, callbackFingerprint(request), success, null, message, 0L);
	}

	private void writeFeedback(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs) {
		String eventCode = request.getEvent() == null ? "UNKNOWN" : request.getEvent().getKey();
		write(request, "FEEDBACK", eventCode, feedbackFingerprint(request), success, httpStatus, message, durationMs);
	}

	private void write(FeedbackReq request, String flowType, String eventCode, String payloadHash, boolean success,
			Integer httpStatus, String message, long durationMs) {
		try {
			String eventId = StringUtils.hasText(request.getReqId()) ? limit(request.getReqId(), 128)
					: UUID.randomUUID().toString();
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", uuid(), flowType, eventId, null, eventCode, payloadHash, request.getAId(), request.getLId(),
					request.getMId(), request.getTId(), success ? "SUCCESS" : "FAILED", httpStatus,
					limit(safe(message), 500), durationMs, LocalDateTime.now());
		}
		catch (RuntimeException error) {
			log.warn("Unable to persist {} flow audit for aId:{}", flowType, request.getAId());
		}
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
