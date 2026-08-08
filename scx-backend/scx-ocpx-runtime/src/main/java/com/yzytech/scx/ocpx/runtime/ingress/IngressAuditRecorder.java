package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Best-effort ingress audit. It intentionally stores only routing metadata, hashes and a
 * masked address; executor saturation or database failure never changes feedback results.
 */
@Component
public class IngressAuditRecorder {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final TaskExecutor executor;

	public IngressAuditRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			@Qualifier("ocpxAuditExecutor") TaskExecutor executor) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.executor = executor;
	}

	public void record(String channelCode, String resultStatus, int httpStatus, String reason, String rawBody,
			HttpHeaders headers, String remoteIp, long durationMs) {
		try {
			executor.execute(() -> persist(channelCode, resultStatus, httpStatus, reason, rawBody, headers, remoteIp,
					durationMs));
		}
		catch (RuntimeException ignored) {
			// Audit is deliberately lossy under overload; the request path must remain
			// available.
		}
	}

	/**
	 * 记录上游 callback 异常。
	 *
	 * <p>
	 * callback 使用 GET query 传参，其中可能包含 ext、签名和外部订单号。本方法会先复制并排序参数， 后台任务只保存完整参数集合的
	 * SHA-256、外部事件号的 SHA-256 引用和脱敏原因，不保存任何原始参数值。
	 * </p>
	 */
	public void recordUpstreamCallback(String upstreamCode, String resultStatus, int httpStatus, String reason,
			Map<String, String> parameters, HttpHeaders headers, String remoteIp, long durationMs) {
		Map<String, String> safeParameters = parameters == null || parameters.isEmpty() ? Collections.emptyMap()
				: Collections.unmodifiableMap(new TreeMap<String, String>(parameters));
		HttpHeaders copiedHeaders = new HttpHeaders();
		if (headers != null) {
			copiedHeaders.putAll(headers);
		}
		HttpHeaders safeHeaders = HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
		try {
			executor.execute(() -> persistUpstreamCallback(upstreamCode, resultStatus, httpStatus, reason,
					safeParameters, safeHeaders, remoteIp, durationMs));
		}
		catch (RuntimeException ignored) {
			// 与 feedback 审计一致：线程池饱和时允许丢审计，绝不能拖垮 callback。
		}
	}

	private void persist(String channelCode, String resultStatus, int httpStatus, String reason, String rawBody,
			HttpHeaders headers, String remoteIp, long durationMs) {
		try {
			JsonNode payload = parse(rawBody);
			String eventId = text(payload, "event_id");
			String routeReference = firstNonBlank(text(payload, "monitor_id"), text(payload, "task_id"));
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_ingress_audit
					(id,request_id,channel_code,event_id,route_reference,result_status,http_status,reason,payload_hash,
					 client_id,key_version,remote_ip_masked,duration_ms,create_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
					""", id(), truncate(headers.getFirst("X-Request-Id"), 128), truncate(channelCode, 64),
					truncate(eventId, 128), truncate(routeReference, 100), truncate(resultStatus, 32), httpStatus,
					sanitizeReason(reason), OcpxHashing.sha256(rawBody == null ? "" : rawBody),
					truncate(headers.getFirst("X-Client-Id"), 128), truncate(headers.getFirst("X-Key-Version"), 32),
					maskIp(remoteIp), durationMs);
		}
		catch (RuntimeException ignored) {
			// No exception may escape this best-effort background path.
		}
	}

	private void persistUpstreamCallback(String upstreamCode, String resultStatus, int httpStatus, String reason,
			Map<String, String> parameters, HttpHeaders headers, String remoteIp, long durationMs) {
		try {
			String normalizedUpstream = normalizeUpstreamCode(upstreamCode);
			String externalEventId = firstNonBlank(parameters.get("conversion_id"), parameters.get("event_id"),
					parameters.get("order_id"));
			String eventReference = externalEventId == null ? null
					: "external-sha256:" + OcpxHashing.sha256(externalEventId);
			String canonicalParameters = objectMapper.writeValueAsString(parameters);
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_ingress_audit
					(id,request_id,channel_code,event_id,route_reference,result_status,http_status,reason,payload_hash,
					 client_id,key_version,remote_ip_masked,duration_ms,create_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
					""", id(), truncate(headers.getFirst("X-Request-Id"), 128),
					truncate("UPSTREAM:" + normalizedUpstream, 64), truncate(eventReference, 128),
					truncate("upstream:" + normalizedUpstream, 100), truncate(resultStatus, 32), httpStatus,
					sanitizeReason(reason), OcpxHashing.sha256(canonicalParameters), truncate(normalizedUpstream, 128),
					truncate(headers.getFirst("X-Key-Version"), 32), maskIp(remoteIp), durationMs);
		}
		catch (Exception ignored) {
			// 序列化、数据库或其他审计异常都不能回流到 callback 请求链路。
		}
	}

	private String normalizeUpstreamCode(String upstreamCode) {
		if (upstreamCode == null || upstreamCode.isBlank()) {
			return "UNKNOWN";
		}
		return upstreamCode.trim().toUpperCase(Locale.ROOT);
	}

	private JsonNode parse(String rawBody) {
		try {
			return rawBody == null ? null : objectMapper.readTree(rawBody);
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private String text(JsonNode payload, String name) {
		JsonNode value = payload == null ? null : payload.get(name);
		return value == null || value.isNull() ? null : value.asText();
	}

	private String maskIp(String remoteIp) {
		if (remoteIp == null || remoteIp.isBlank()) {
			return null;
		}
		if (remoteIp.contains(":")) {
			int split = remoteIp.indexOf(':');
			return remoteIp.substring(0, Math.min(split + 1, remoteIp.length())) + "***";
		}
		String[] parts = remoteIp.split("\\.");
		return parts.length == 4 ? parts[0] + "." + parts[1] + ".***.***" : "***";
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String truncate(String value, int length) {
		return value == null || value.length() <= length ? value : value.substring(0, length);
	}

	private String sanitizeReason(String reason) {
		if (reason == null) {
			return null;
		}
		String sanitized = reason.replaceAll("[\\r\\n\\t]", " ")
			.replaceAll(
					"(?i)(sign(?:ature)?|secret|token|callback|ext|imei|oaid|idfa|android[_-]?id)\\s*[:=]\\s*[^,;\\s]+",
					"$1=[REDACTED]")
			.replaceAll("\\b[0-9]{8,}\\b", "[REDACTED]")
			.replaceAll("\\b[A-Fa-f0-9]{32,}\\b", "[REDACTED]");
		return truncate(sanitized, 1000);
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
