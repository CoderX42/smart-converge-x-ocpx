package com.yzytech.scx.ocpx.report.service;

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

/** 将协议路由拒绝写入现有脱敏流程日志，不保存设备标识、URL 或凭据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcpxFlowLogWriter {

	private final JdbcTemplate jdbcTemplate;

	public void rejectedAdvertiser(FeedbackReq request) {
		write(request, false, null, "ADVERTISER_PROTOCOL_NOT_REGISTERED", 0L);
	}

	/** 记录 AERIM 出站结果，不保存请求 URL、响应正文或设备标识。 */
	public void upstreamDelivery(FeedbackReq request, boolean success, Integer httpStatus, String message,
			long durationMs) {
		write(request, success, httpStatus, message, durationMs);
	}

	private void write(FeedbackReq request, boolean success, Integer httpStatus, String message, long durationMs) {
		try {
			String eventId = StringUtils.hasText(request.getReqId()) ? limit(request.getReqId(), 128)
					: UUID.randomUUID().toString();
			String fingerprint = fingerprint(request);
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", uuid(), "FEEDBACK", eventId, null,
					request.getEvent() == null ? "UNKNOWN" : request.getEvent().getKey(), fingerprint, request.getAId(),
					request.getLId(), request.getMId(), request.getTId(), success ? "SUCCESS" : "FAILED", httpStatus,
					limit(safe(message), 500), durationMs, LocalDateTime.now());
		}
		catch (RuntimeException error) {
			log.warn("Unable to persist advertiser delivery audit for aId:{}", request.getAId());
		}
	}

	private String fingerprint(FeedbackReq request) {
		try {
			String value = String.join("|", safe(request.getAId()), safe(request.getLId()), safe(request.getMId()),
					safe(request.getTId()), request.getEvent() == null ? "" : request.getEvent().name(),
					safe(request.getReqId()));
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

	private String limit(String value, int length) {
		return value.length() <= length ? value : value.substring(0, length);
	}

}
