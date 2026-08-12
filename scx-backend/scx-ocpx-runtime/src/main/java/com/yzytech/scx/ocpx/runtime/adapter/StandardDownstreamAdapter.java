package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.DownstreamDeliveryContext;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
		return new AdapterCapabilities("DOWNSTREAM", Set.of(OcpxEventType.values()), Set.of("POST"),
				Set.of("NONE", "TOKEN", "SIGNATURE"), Duration.ofSeconds(3), Set.of(), true);
	}

	@Override
	public AdapterHttpRequest buildCallback(DownstreamDeliveryContext context) {
		try {
			String body = objectMapper.writeValueAsString(context.callback());
			URI target = URI.create(context.partner().downstreamCallbackUrl());
			Map<String, String> headers = new LinkedHashMap<>(
					AdapterRequestSupport.mediaHeaders(context, "POST", target, body));
			headers.put("Content-Type", "application/json");
			headers.put("Idempotency-Key", context.callback().eventId());
			return new AdapterHttpRequest("POST", target, Map.copyOf(headers), body);
		}
		catch (Exception error) {
			throw new IllegalStateException("downstream callback cannot be encoded", error);
		}
	}

	@Override
	public AdapterDeliveryResult evaluateCallbackResponse(AdapterHttpResponse response) {
		return AdapterRequestSupport.http2xx(response, "DOWNSTREAM");
	}

}
