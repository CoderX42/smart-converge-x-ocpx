package com.yzytech.scx.ocpx.report.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 写入按 trace 聚合的结构化链路日志；永不保存原始 URL、ext、签名或凭据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcpxFlowLogWriter {

	private static final int RESPONSE_PREVIEW_LIMIT = 4096;

	private static final int REQUEST_DETAIL_LIMIT = 16384;

	private static final String SENSITIVE_KEY = "access_token|api_key|client_secret|password|satoken|secret|signature|sign|token|key|ext";

	private static final Pattern JSON_SECRET = Pattern
		.compile("(?i)(\\\"(?:" + SENSITIVE_KEY + ")\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");

	private static final Pattern PARAM_SECRET = Pattern
		.compile("(?i)((?:^|[?&\\s])(?:" + SENSITIVE_KEY + ")=)[^&\\s]*");

	private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/-]+=*");

	private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

	/** 成功日志采样率；失败日志始终保留。 */
	@Value("${ocpx.flow-log.success-sample-rate:1.0}")
	private double successSampleRate = 1.0;

	private final JdbcTemplate jdbcTemplate;

	public void mediaIngress(FeedbackReq request, boolean success, String message) {
		write(request, "FEEDBACK", "MEDIA_INGRESS", event(request), feedbackFingerprint(request), success, null,
				message, null, 0L, "GET", "/feedback", null, normalizedRequest(request));
	}

	public void rejectedAdvertiser(FeedbackReq request) {
		write(request, "FEEDBACK", "ADVERTISER_DELIVERY", event(request), feedbackFingerprint(request), false, null,
				"ADVERTISER_PROTOCOL_NOT_REGISTERED", null, 0L, "GET", null, null, null);
	}

	public void upstreamDelivery(FeedbackReq request, boolean success, Integer status, String message, long duration) {
		upstreamDelivery(request, success, status, message, duration, null);
	}

	public void upstreamDelivery(FeedbackReq request, boolean success, Integer status, String message, long duration,
			String response) {
		upstreamDelivery(request, success, status, message, duration, response, null, null);
	}

	public void upstreamDelivery(FeedbackReq request, boolean success, Integer status, String message, long duration,
			String response, String endpoint, Map<String, ?> macroAudit) {
		write(request, "FEEDBACK", "ADVERTISER_DELIVERY", event(request), feedbackFingerprint(request), success, status,
				message, responsePreview(response), duration, "GET", safeEndpoint(endpoint), macroAudit, null);
	}

	public void callback(CallbackReq request, boolean success, String message) {
		if (!hasFeedback(request)) {
			log.warn("Unable to persist callback audit without feedback context");
			return;
		}
		write(request.getFeedback(), "CALLBACK", "ADVERTISER_CALLBACK", action(request), callbackFingerprint(request),
				success, null, message, null, 0L, "GET", "/callback", null, null);
	}

	public void accounting(CallbackReq request, boolean success, String message) {
		internal(request, "CALLBACK_ACCOUNTING", success, message);
	}

	public void deduction(CallbackReq request, boolean success, String message) {
		internal(request, "MEDIA_DEDUCTION", success, message);
	}

	public void downstreamDelivery(CallbackReq request, boolean success, Integer status, String message, long duration,
			String response, String endpoint) {
		if (hasFeedback(request)) {
			write(request.getFeedback(), "CALLBACK", "MEDIA_DELIVERY", action(request), callbackFingerprint(request),
					success, status, message, responsePreview(response), duration, "GET", safeEndpoint(endpoint), null,
					null);
		}
	}

	private void internal(CallbackReq request, String stage, boolean success, String message) {
		if (hasFeedback(request)) {
			write(request.getFeedback(), "CALLBACK", stage, action(request), callbackFingerprint(request), success,
					null, message, null, 0L, null, null, null, null);
		}
	}

	private void write(FeedbackReq request, String type, String stage, String eventCode, String payloadHash,
			boolean success, Integer status, String message, String response, long duration, String method,
			String endpoint, Map<String, ?> macroAudit, Map<String, Object> requestParams) {
		if (sampleableSuccess(success, message) && !sampleSuccess(payloadHash)) {
			return;
		}
		try {
			String id = uuid();
			String eventId = StringUtils.hasText(request.getReqId()) ? limit(request.getReqId(), 128)
					: UUID.randomUUID().toString();
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,trace_id,flow_type,stage_code,event_id,source_event_id,event_code,request_method,
					 request_endpoint,macro_audit,payload_hash,advertiser_id,promotion_link_id,media_id,
					 monitor_task_id,result_status,partner_http_status,message,response_preview,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", id, traceId(request), type, stage, eventId, null, eventCode, method, endpoint,
					json(macroAudit), payloadHash, request.getAId(), request.getLId(), request.getMId(),
					request.getTId(), success ? "SUCCESS" : "FAILED", status, limit(message, 500), response, duration,
					LocalDateTime.now());
			writeDetail(id, requestParams, response);
		}
		catch (RuntimeException error) {
			log.warn("Unable to persist {} flow audit for aId:{}", stage, request.getAId());
		}
	}

	private double normalizedSampleRate() {
		if (Double.isNaN(successSampleRate) || successSampleRate <= 0) {
			return 0;
		}
		return Math.min(successSampleRate, 1.0);
	}

	private boolean sampleableSuccess(boolean success, String message) {
		return success || "CALLBACK_ACCOUNTING_DUPLICATE".equals(message)
				|| "MEDIA_DEDUCTION_ALREADY_FINALIZED".equals(message);
	}

	private boolean sampleSuccess(String samplingKey) {
		double rate = normalizedSampleRate();
		if (rate <= 0) {
			return false;
		}
		if (rate >= 1) {
			return true;
		}
		int bucket = Math.floorMod(samplingKey == null ? 0 : samplingKey.hashCode(), 10_000);
		return bucket < Math.round(rate * 10_000);
	}

	private void writeDetail(String flowId, Map<String, Object> params, String response) {
		String requestJson = json(params);
		boolean truncated = requestJson != null && requestJson.length() > REQUEST_DETAIL_LIMIT;
		if (truncated) {
			requestJson = JSON.toJSONString(Map.of("_truncated", true));
		}
		if (requestJson != null || response != null) {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log_detail
					(id,flow_log_id,request_params,response_preview,request_truncated,expires_at)
					VALUES (?,?,?,?,?,?)
					""", uuid(), flowId, requestJson, response, truncated, LocalDateTime.now().plusDays(7));
		}
	}

	private Map<String, Object> normalizedRequest(FeedbackReq v) {
		Map<String, Object> m = new LinkedHashMap<>();
		put(m, "request_id", v.getReqId());
		put(m, "event", event(v));
		put(m, "caid", v.getCaid());
		put(m, "caid_list", v.getCaidList());
		put(m, "caid1", v.getCaid1());
		put(m, "caid2", v.getCaid2());
		put(m, "last_caid", v.getLastCaid());
		put(m, "prev_caid", v.getPrevCaid());
		put(m, "idfa", v.getIdfa());
		put(m, "idfa_md5", v.getIdfaMd5());
		put(m, "aaid", v.getAaid());
		put(m, "imei", v.getImei());
		put(m, "imei_md5", v.getImeiMd5());
		put(m, "oaid", v.getOaid());
		put(m, "oaid_md5", v.getOaidMd5());
		put(m, "androidid", v.getAndroidid());
		put(m, "androidid_md5", v.getAndroididMd5());
		put(m, "mac", v.getMac());
		put(m, "mac_md5", v.getMacMd5());
		put(m, "ip", v.getIp());
		put(m, "ipv6", v.getIpv6());
		put(m, "os", v.getOs());
		put(m, "osv", v.getOsv());
		put(m, "model", v.getModel());
		put(m, "ts", v.getTs());
		put(m, "mts", v.getTsMs());
		put(m, "ua", v.getUa());
		put(m, "adv_id", v.getAdvId());
		put(m, "group_id", v.getGroupId());
		put(m, "plan_id", v.getPlanId());
		put(m, "creative_id", v.getCreativeId());
		put(m, "impresss_id", v.getImpresssId());
		put(m, "clickid", v.getClickId());
		return m;
	}

	private void put(Map<String, Object> values, String key, Object value) {
		if (value != null && StringUtils.hasText(value.toString()))
			values.put(key, value);
	}

	private String traceId(FeedbackReq r) {
		return fingerprint(String.join("|", safe(r.getMId()), safe(r.getTId()), safe(r.getAId()), safe(r.getLId()),
				safe(r.getReqId())));
	}

	private String safeEndpoint(String value) {
		if (!StringUtils.hasText(value))
			return null;
		try {
			URI uri = URI.create(value);
			return limit(uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath()), 512);
		}
		catch (IllegalArgumentException error) {
			return null;
		}
	}

	private String responsePreview(String body) {
		if (!StringUtils.hasText(body))
			return null;
		String value = UNSAFE_CONTROL.matcher(body).replaceAll("");
		value = JSON_SECRET.matcher(value).replaceAll("$1***$2");
		value = PARAM_SECRET.matcher(value).replaceAll("$1***");
		value = BEARER_SECRET.matcher(value).replaceAll("$1***");
		return limit(value, RESPONSE_PREVIEW_LIMIT);
	}

	private boolean hasFeedback(CallbackReq r) {
		return r != null && r.getFeedback() != null;
	}

	private String event(FeedbackReq r) {
		return r.getEvent() == null ? "UNKNOWN" : r.getEvent().getKey();
	}

	private String action(CallbackReq r) {
		return r.getActionType() == null ? "UNKNOWN" : r.getActionType().name();
	}

	private String feedbackFingerprint(FeedbackReq r) {
		return fingerprint(traceId(r) + "|" + event(r));
	}

	private String callbackFingerprint(CallbackReq r) {
		return fingerprint(traceId(r.getFeedback()) + "|" + action(r));
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

	private String json(Object value) {
		return value == null ? null : JSON.toJSONString(value);
	}

	private String uuid() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private String safe(Object value) {
		return value == null ? "" : value.toString();
	}

	private String limit(String value, int length) {
		return value == null || value.length() <= length ? value : value.substring(0, length);
	}

}
