package com.yzytech.scx.ocpx.runtime.adapter;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class TemplateGetUpstreamAdapter implements UpstreamAdapter {

	@Override
	public String code() {
		return "TEMPLATE_GET";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("UPSTREAM", Set.of(OcpxEventType.values()), Set.of("GET"), Set.of("NONE"),
				Duration.ofSeconds(3), Set.of("__CALLBACK__", "__EVENTID__", "__CLICKID__"), true);
	}

	@Override
	public boolean supports(OcpxEventType eventType) {
		return true;
	}

	@Override
	public AdapterHttpRequest buildFeedback(UpstreamDeliveryContext context) {
		return AdapterRequestSupport.upstreamTemplateGet(context);
	}

	@Override
	public AdapterDeliveryResult evaluateFeedbackResponse(AdapterHttpResponse response) {
		return AdapterRequestSupport.http2xx(response, "UPSTREAM");
	}

	@Override
	public Mono<String> callbackToken(Map<String, String> parameters) {
		return Mono.justOrEmpty(parameters.get("ext"));
	}

	@Override
	public Mono<NormalizedCallbackEvent> parseCallback(String callbackRef, Map<String, String> parameters) {
		return Mono.fromCallable(() -> {
			String event = parameters.getOrDefault("event", parameters.get("action_type"));
			if (event == null || event.isBlank()) {
				throw new IllegalArgumentException("event is required");
			}
			String external = first(parameters.get("event_id"), parameters.get("conversion_id"),
					parameters.get("order_id"));
			Map<String, String> identity = new TreeMap<>(parameters);
			identity.remove("ext");
			identity.remove("s");
			String id = OcpxHashing.sha256(callbackRef + "|" + (external == null ? identity : external));
			String amountRaw = first(parameters.get("currency_amount"), parameters.get("amount"));
			return new NormalizedCallbackEvent(id, null, callbackRef, OcpxEventType.fromExternalOrUnknown(event),
					actionTime(parameters), decimal(amountRaw), amountRaw).withSourceEventCode(event);
		});
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackSuccess(Map<String, String> parameters, String eventId,
			String status) {
		return Mono.just(
				new UpstreamCallbackResponse(200, Map.of(), Map.of("code", 0, "event_id", eventId, "status", status)));
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackFailure(Map<String, String> parameters, int status, String message) {
		return Mono.just(new UpstreamCallbackResponse(status, Map.of(),
				Map.of("code", 1, "message", message == null ? "callback rejected" : message)));
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
			return value == null ? null : new BigDecimal(value);
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

}
