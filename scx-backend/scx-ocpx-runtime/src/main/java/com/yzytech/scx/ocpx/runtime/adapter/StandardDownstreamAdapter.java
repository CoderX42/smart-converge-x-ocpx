package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Component
public class StandardDownstreamAdapter implements DownstreamAdapter {

	private final ObjectMapper objectMapper;

	public StandardDownstreamAdapter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String code() {
		return "STANDARD";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("DOWNSTREAM", Set.of(OcpxEventType.values()), Set.of("POST"), Set.of("HMAC"),
				Duration.ofSeconds(3), Set.of(), true);
	}

	@Override
	public FeedbackReqV1 parseFeedback(String rawBody) {
		try {
			FeedbackReqV1 request = objectMapper.readValue(rawBody, FeedbackReqV1.class);
			if (request.getEvent() == null || request.getEvent().isBlank()) {
				throw new IllegalArgumentException("event is required");
			}
			return request;
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("invalid FeedbackReq payload", e);
		}
	}

	@Override
	public AdapterHttpRequest buildCallback(RouteSnapshot route, CallbackReqV1 callback) {
		if (route.downstreamCallbackUrl() == null || route.downstreamCallbackUrl().isBlank()) {
			throw new IllegalArgumentException("downstream callback URL is not configured");
		}
		try {
			String body = objectMapper.writeValueAsString(callback);
			String timestamp = Long.toString(Instant.now().toEpochMilli());
			String nonce = UUID.randomUUID().toString().replace("-", "");
			URI uri = URI.create(route.downstreamCallbackUrl());
			String canonical = "POST\n" + uri.getRawPath() + "\n" + timestamp + "\n" + nonce + "\n"
					+ OcpxHashing.sha256(body);
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Content-Type", "application/json");
			headers.put("X-Client-Id", route.channelCode());
			headers.put("X-Key-Version", Integer.toString(route.channelKeyVersion()));
			headers.put("X-Timestamp", timestamp);
			headers.put("X-Nonce", nonce);
			headers.put("X-Sign", OcpxHashing.hmacSha256(route.channelSecret(), canonical));
			headers.put("Idempotency-Key", callback.eventId());
			return new AdapterHttpRequest("POST", uri, headers, body);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("cannot serialize downstream callback", e);
		}
	}

	@Override
	public boolean isSuccess(AdapterHttpResponse response) {
		return response.statusCode() >= 200 && response.statusCode() < 300;
	}

}
