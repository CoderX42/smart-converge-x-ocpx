package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.contract.UpstreamDeliveryContext;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class CocoUpstreamAdapter implements UpstreamAdapter {

	private final ObjectMapper objectMapper;

	public CocoUpstreamAdapter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String code() {
		return "COCO";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("UPSTREAM", Set.of(OcpxEventType.CLICK), Set.of("GET"), Set.of("NONE"),
				Duration.ofSeconds(3), Set.of("__CALLBACK__", "__CLICKID__", "__OAID__", "__IDFA__"), true);
	}

	@Override
	public boolean supports(OcpxEventType eventType) {
		return eventType == OcpxEventType.CLICK;
	}

	@Override
	public AdapterHttpRequest buildFeedback(UpstreamDeliveryContext context) {
		return AdapterRequestSupport.upstreamTemplateGet(context);
	}

	@Override
	public AdapterDeliveryResult evaluateFeedbackResponse(AdapterHttpResponse response) {
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			return AdapterDeliveryResult.failure("COCO_HTTP_" + response.statusCode(), null, null);
		}
		if (response.body() == null || response.body().isBlank()) {
			return AdapterDeliveryResult.success(null, null);
		}
		try {
			JsonNode body = objectMapper.readTree(response.body());
			JsonNode codeNode = body.get("code");
			if (codeNode == null || codeNode.isNull()) {
				return AdapterDeliveryResult.success(null, null);
			}
			String code = codeNode.asText();
			String message = body.hasNonNull("message") ? truncate(body.get("message").asText(), 256) : null;
			if ("0".equals(code) || "200".equals(code)) {
				return AdapterDeliveryResult.success(code, message);
			}
			return AdapterDeliveryResult.failure("COCO_BUSINESS_" + safeCode(code), code, message);
		}
		catch (Exception error) {
			return AdapterDeliveryResult.failure("COCO_RESPONSE_INVALID", null, null);
		}
	}

	@Override
	public Mono<String> callbackToken(Map<String, String> parameters) {
		return Mono.justOrEmpty(parameters.get("ext"));
	}

	@Override
	public Mono<NormalizedCallbackEvent> parseCallback(String callbackRef, Map<String, String> parameters) {
		return Mono.fromCallable(() -> {
			String sourceCode = required(parameters, "action_type");
			String externalId = first(parameters.get("conversion_id"), parameters.get("event_id"),
					parameters.get("order_id"));
			Map<String, String> identity = new TreeMap<>(parameters);
			identity.remove("ext");
			identity.remove("s");
			String eventId = OcpxHashing.sha256(callbackRef + "|" + (externalId == null ? identity : externalId));
			String amountRaw = parameters.get("currency_amount");
			BigDecimal amount = decimal(amountRaw);
			return new NormalizedCallbackEvent(eventId, null, callbackRef,
					OcpxEventType.fromExternalOrUnknown(sourceCode), actionTime(parameters), amount, amountRaw)
				.withSourceEventCode(sourceCode);
		});
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackSuccess(Map<String, String> parameters, String eventId,
			String status) {
		return Mono.just(
				new UpstreamCallbackResponse(200, Map.of(), Map.of("code", 0, "event_id", eventId, "status", status)));
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackFailure(Map<String, String> parameters, int httpStatus,
			String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", 1);
		body.put("message", message == null ? "callback rejected" : message);
		return Mono.just(new UpstreamCallbackResponse(httpStatus, Map.of(), body));
	}

	private String required(Map<String, String> values, String key) {
		String value = values.get(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(key + " is required");
		}
		return value;
	}

	private String first(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private BigDecimal decimal(String value) {
		try {
			return value == null || value.isBlank() ? null : new BigDecimal(value);
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	private OffsetDateTime actionTime(Map<String, String> parameters) {
		String value = first(parameters.get("action_time"), parameters.get("event_time"), parameters.get("timestamp"),
				parameters.get("ts"));
		if (value == null) {
			return OffsetDateTime.now(ZoneOffset.UTC);
		}
		try {
			long epoch = Long.parseLong(value);
			long millis = epoch < 10_000_000_000L ? Math.multiplyExact(epoch, 1000L) : epoch;
			return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
		}
		catch (ArithmeticException | NumberFormatException ignored) {
			return OffsetDateTime.parse(value);
		}
	}

	private String safeCode(String value) {
		String safe = value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_-]", "_");
		return truncate(safe.toUpperCase(), 48);
	}

	private String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
