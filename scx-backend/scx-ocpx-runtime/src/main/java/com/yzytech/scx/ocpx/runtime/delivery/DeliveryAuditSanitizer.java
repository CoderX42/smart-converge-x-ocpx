package com.yzytech.scx.ocpx.runtime.delivery;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.runtime.model.DeliveryAttemptAudit;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Creates request evidence without retaining credentials, callback tokens or device data.
 */
@Component
public class DeliveryAuditSanitizer {

	public DeliveryAttemptAudit summarize(AdapterHttpRequest request, AdapterHttpResponse response, long durationMs) {
		URI uri = request.uri();
		String query = uri.getRawQuery();
		String requestSummary = request.method() + " " + safePath(uri) + (query == null ? "" : "?query_sha256="
				+ OcpxHashing.sha256(query) + "&query_bytes=" + query.getBytes(StandardCharsets.UTF_8).length);
		String responseExcerpt = response == null ? null
				: "body_sha256=" + OcpxHashing.sha256(response.body() == null ? "" : response.body()) + ";body_bytes="
						+ (response.body() == null ? 0 : response.body().getBytes(StandardCharsets.UTF_8).length);
		return new DeliveryAttemptAudit(request.method(), uri.getHost(), safePath(uri), requestSummary, responseExcerpt,
				durationMs);
	}

	private String safePath(URI uri) {
		String rawPath = uri.getRawPath();
		if (rawPath == null || rawPath.isBlank()) {
			return "/";
		}
		// Callback URLs frequently put a webhook key or access token directly in a path
		// segment (for example: /<uuid>). Query-string hashing alone cannot protect that
		// value. Keep normal endpoint names readable, but replace long token-like segments
		// with a short hash so attempts remain useful for troubleshooting without retaining
		// the complete callback credential.
		return Arrays.stream(rawPath.split("/", -1)).map(this::sanitizePathSegment).collect(Collectors.joining("/"));
	}

	private String sanitizePathSegment(String segment) {
		if (segment.length() < 24) {
			return segment;
		}
		return "{redacted_sha256=" + OcpxHashing.sha256(segment).substring(0, 16) + ";bytes="
				+ segment.getBytes(StandardCharsets.UTF_8).length + "}";
	}

}
