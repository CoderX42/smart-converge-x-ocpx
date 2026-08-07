package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
