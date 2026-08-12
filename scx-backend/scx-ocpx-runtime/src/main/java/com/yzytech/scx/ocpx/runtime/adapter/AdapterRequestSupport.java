package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.CallbackReqV2;
import com.yzytech.scx.ocpx.contract.DownstreamDeliveryContext;
import com.yzytech.scx.ocpx.contract.FeedbackReqV2;
import com.yzytech.scx.ocpx.contract.UpstreamDeliveryContext;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AdapterRequestSupport {

	private static final Set<String> UNTRUSTED_DIMENSIONS = Set.of("advertiser_id", "promotion_link_id", "media_id",
			"monitor_task_id");

	private AdapterRequestSupport() {
	}

	static AdapterHttpRequest upstreamTemplateGet(UpstreamDeliveryContext context) {
		FeedbackReqV2 feedback = context.feedback();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("__CALLBACK__", context.callbackUrl());
		values.put("__CALLBACK_URL__", context.callbackUrl());
		values.put("__CLICKID__", feedback.getClickId());
		values.put("__EVENTID__", feedback.getEventId());
		values.put("__EVENT_ID__", feedback.getEventId());
		values.put("__EVENT__", context.upstreamEventCode());
		values.put("__EVENTTYPE__", context.upstreamEventCode());
		values.put("__EVENT_TYPE__", context.upstreamEventCode());
		values.put("__EVENT_TIME__", string(feedback.getEventTime()));
		feedback.getDevice().forEach((key, value) -> values.put(macro(key), string(value)));
		feedback.extensions().forEach((key, value) -> {
			String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
			if (!UNTRUSTED_DIMENSIONS.contains(normalized)) {
				values.put(macro(normalized), string(value));
			}
		});
		URI target = expand(context.partner().bidUrl(), values);
		Map<String, String> headers = context.account() == null || context.account().isBlank() ? Map.of()
				: Map.of("X-OCPX-Account", context.account());
		return new AdapterHttpRequest("GET", target, headers, null);
	}

	static AdapterHttpRequest downstreamTemplateGet(DownstreamDeliveryContext context) {
		CallbackReqV2 callback = context.callback();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("__EVENT__", callback.event());
		values.put("__EVENT_ID__", callback.eventId());
		values.put("__SOURCE_EVENT_ID__", callback.sourceEventId());
		values.put("__CLICKID__", callback.clickId());
		values.put("__MONITOR_TASK_ID__", callback.monitorTaskId());
		values.put("__ACTION_TIME__", string(callback.actionTime()));
		values.put("__AMOUNT__", string(callback.amount()));
		values.put("__AMOUNT_RAW__", callback.amountRaw());
		URI target = expand(context.partner().downstreamCallbackUrl(), values);
		Map<String, String> headers = mediaHeaders(context, "GET", target, "");
		headers.put("Idempotency-Key", callback.eventId());
		return new AdapterHttpRequest("GET", target, Map.copyOf(headers), null);
	}

	static Map<String, String> mediaHeaders(DownstreamDeliveryContext context, String method, URI target, String body) {
		Map<String, String> headers = new LinkedHashMap<>();
		String authType = context.partner().mediaAuthType();
		if ("TOKEN".equals(authType)) {
			headers.put("Authorization", "Bearer " + required(context.secret(), "media token"));
		}
		else if ("SIGNATURE".equals(authType)) {
			String timestamp = Long.toString(System.currentTimeMillis());
			String nonce = UUID.randomUUID().toString().replace("-", "");
			headers.put("X-OCPX-Key-Version", Long.toString(context.partner().mediaCredentialVersion()));
			headers.put("X-OCPX-Timestamp", timestamp);
			headers.put("X-OCPX-Nonce", nonce);
			headers.put("X-OCPX-Signature", hmac(required(context.secret(), "media signature secret"),
					method + "\n" + target.getRawPath() + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body)));
		}
		return headers;
	}

	static AdapterDeliveryResult http2xx(AdapterHttpResponse response, String prefix) {
		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return AdapterDeliveryResult.success(null, null);
		}
		return AdapterDeliveryResult.failure(prefix + "_HTTP_" + response.statusCode(), null, null);
	}

	private static URI expand(String template, Map<String, String> values) {
		if (template == null || template.isBlank()) {
			throw new IllegalArgumentException("adapter URL template is required");
		}
		String expanded = template;
		for (Map.Entry<String, String> value : values.entrySet()) {
			if (value.getValue() != null) {
				expanded = expanded.replace(value.getKey(),
						UriUtils.encodeQueryParam(value.getValue(), StandardCharsets.UTF_8));
			}
		}
		if (expanded.matches(".*__[A-Z0-9_]+__.*")) {
			throw new IllegalArgumentException("adapter URL contains unresolved macro");
		}
		return URI.create(expanded);
	}

	private static String macro(String name) {
		return "__" + name.toUpperCase(Locale.ROOT) + "__";
	}

	private static String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static String required(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}

	private static String hmac(String secret, String canonical) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot create downstream signature", error);
		}
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot hash downstream payload", error);
		}
	}

}
