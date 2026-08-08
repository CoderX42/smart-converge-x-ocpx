package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class CocoUpstreamAdapter implements UpstreamAdapter {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> SUCCESS_CODES = Set.of("0", "200");

	@Override
	public String code() {
		return "COCO";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("UPSTREAM", Set.of(OcpxEventType.CLICK), Set.of("GET"), Set.of("NONE"),
				Duration.ofSeconds(3),
				Set.of("__IDFA__", "__IDFA_MD5__", "__IMEI__", "__IMEI_MD5__", "__OAID__", "__OAID_MD5__", "__IP__",
						"__CAID__", "__CAID_LIST__", "__ANDROIDID__", "__ANDROID_ID__", "__ANDROIDID_MD5__",
						"__ANDROID_ID_MD5__", "__AAID__", "__UA__", "__OS__", "__MODEL__", "__CALLBACK__",
						"__CLICKID__", "__CAMPAIGNID__", "__ADGROUPID__", "__CREATIVEID__", "__ADVERTISERID__"),
				true);
	}

	@Override
	public boolean supports(OcpxEventType eventType) {
		return eventType == OcpxEventType.CLICK;
	}

	@Override
	public Mono<Void> validateFeedback(RouteSnapshot route, FeedbackReqV1 feedback) {
		return Mono.fromRunnable(() -> validateFeedbackRequest(route, feedback));
	}

	private void validateFeedbackRequest(RouteSnapshot route, FeedbackReqV1 feedback) {
		OcpxEventType eventType = OcpxEventType.fromExternal(feedback.getEvent());
		if (eventType != OcpxEventType.CLICK) {
			throw new IllegalArgumentException("Coco only accepts CLICK feedback");
		}
		if (route.bidUrl() == null || !route.bidUrl().contains("__CALLBACK__")) {
			throw new IllegalArgumentException("Coco URL must contain __CALLBACK__");
		}
		if (!isExplicitIos(feedback) && firstNonBlank(feedback.getImei(), feedback.getImeiMd5(), feedback.getOaid(),
				feedback.getOaidMd5()) == null) {
			throw new IllegalArgumentException("Android feedback requires IMEI/OAID raw or MD5 value");
		}
		validateLength("ua", feedback.getUa(), 1000);
		validateLength("caid", feedback.getCaid(), 5000);
		validateLength("imei", feedback.getImei(), 512);
		validateLength("imei_md5", feedback.getImeiMd5(), 512);
		validateLength("oaid", feedback.getOaid(), 512);
		validateLength("oaid_md5", feedback.getOaidMd5(), 512);
		for (String required : requiredMacros(route)) {
			if (!hasMacroValue(required, feedback)) {
				throw new IllegalArgumentException("Coco required macro has no value: " + required);
			}
		}
	}

	@Override
	public Mono<AdapterHttpRequest> buildFeedback(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl) {
		return Mono.fromCallable(() -> buildFeedbackRequest(route, feedback, callbackUrl));
	}

	private AdapterHttpRequest buildFeedbackRequest(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl) {
		validateLength("callback", callbackUrl, 5000);
		Map<String, String> macros = new LinkedHashMap<>();
		macros.put("__IDFA__", feedback.getIdfa());
		macros.put("__IDFA_MD5__", feedback.getIdfaMd5());
		macros.put("__IMEI__", feedback.getImei());
		macros.put("__IMEI_MD5__", feedback.getImeiMd5());
		macros.put("__OAID__", feedback.getOaid());
		macros.put("__OAID_MD5__", feedback.getOaidMd5());
		macros.put("__IP__", feedback.getIp());
		macros.put("__CAID__", feedback.getCaid());
		macros.put("__CAID_LIST__", feedback.getCaid());
		macros.put("__ANDROIDID__", feedback.getAndroidId());
		macros.put("__ANDROID_ID__", feedback.getAndroidId());
		macros.put("__ANDROIDID_MD5__", feedback.getAndroidIdMd5());
		macros.put("__ANDROID_ID_MD5__", feedback.getAndroidIdMd5());
		macros.put("__AAID__", feedback.getAaid());
		macros.put("__UA__", feedback.getUa());
		macros.put("__OS__", convertOs(feedback.getOs()));
		macros.put("__MODEL__", feedback.getModel());
		macros.put("__CALLBACK__", callbackUrl);
		macros.put("__CLICKID__", feedback.getClickid());
		macros.put("__CAMPAIGNID__", feedback.getCampaignId());
		macros.put("__ADGROUPID__", feedback.getAdgroupId());
		macros.put("__CREATIVEID__", feedback.getCreativeId());
		macros.put("__ADVERTISERID__", feedback.getAdvertiserId());
		Set<String> required = requiredMacros(route);
		String url = route.bidUrl();
		for (Map.Entry<String, String> entry : macros.entrySet()) {
			String replacement = entry.getValue() == null || entry.getValue().isBlank()
					? (required.contains(entry.getKey()) ? entry.getKey() : "") : encode(entry.getValue());
			url = url.replace(entry.getKey(), replacement);
		}
		if (url.matches(".*__[A-Z0-9_]+__.*")) {
			throw new IllegalArgumentException("Coco URL contains unresolved required macros");
		}
		if (url.length() > 16_384) {
			throw new IllegalArgumentException("Coco URL exceeds safe length limit");
		}
		return new AdapterHttpRequest("GET", URI.create(url), Map.of(), null);
	}

	@Override
	public Mono<AdapterDeliveryResult> evaluateFeedbackResponse(AdapterHttpResponse response) {
		return Mono.fromSupplier(() -> {
			if (response == null) {
				return AdapterDeliveryResult.failure("COCO_NO_RESPONSE", null, null);
			}
			String code = businessCode(response.body());
			String message = businessMessage(response.body());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return AdapterDeliveryResult.failure("COCO_HTTP_" + response.statusCode(), code, message);
			}
			// Coco 文档允许成功响应为空；只有明确返回非成功业务 code 时才进入重试。
			if (code == null || SUCCESS_CODES.contains(code)) {
				return AdapterDeliveryResult.success(code, message);
			}
			return AdapterDeliveryResult
				.failure(truncate("COCO_CODE_" + code + (message == null ? "" : ":" + message), 1000), code, message);
		});
	}

	@Override
	public Mono<String> callbackToken(Map<String, String> parameters) {
		return Mono.justOrEmpty(parameters.get("ext"));
	}

	@Override
	public Mono<NormalizedCallbackEvent> parseCallback(String callbackRef, Map<String, String> parameters) {
		return Mono.fromCallable(() -> parseCallbackEvent(callbackRef, parameters));
	}

	private NormalizedCallbackEvent parseCallbackEvent(String callbackRef, Map<String, String> parameters) {
		OcpxEventType eventType = OcpxEventType.fromExternal(parameters.get("action_type"));
		String amountRaw = eventType == OcpxEventType.PAYMENT ? parameters.get("currency_amount") : null;
		BigDecimal amount = null;
		if (amountRaw != null && !amountRaw.isBlank()) {
			if (amountRaw.length() > 64) {
				throw new IllegalArgumentException("currency_amount is too long");
			}
			try {
				BigDecimal candidate = new BigDecimal(amountRaw);
				int integerDigits = candidate.precision() - candidate.scale();
				if (candidate.signum() >= 0 && candidate.scale() <= 6 && integerDigits <= 14) {
					amount = candidate;
				}
			}
			catch (NumberFormatException ignored) {
				// Keep the raw value for audit; billing is disabled in the first release.
			}
		}
		Map<String, String> identityParameters = new TreeMap<>(parameters);
		identityParameters.remove("ext");
		identityParameters.remove("s");
		String externalEventId = firstNonBlank(parameters.get("conversion_id"), parameters.get("event_id"),
				parameters.get("order_id"));
		String eventId = externalEventId == null ? OcpxHashing.sha256(callbackRef + "|" + identityParameters)
				: OcpxHashing.sha256(callbackRef + "|external|" + externalEventId);
		return new NormalizedCallbackEvent(eventId, null, callbackRef, eventType, OffsetDateTime.now(), amount,
				amountRaw);
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackSuccess(Map<String, String> parameters, String eventId,
			String status) {
		return Mono.fromSupplier(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("code", 0);
			body.put("event_id", eventId);
			body.put("status", status);
			return new UpstreamCallbackResponse(200, Map.of(), body);
		});
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackFailure(Map<String, String> parameters, int httpStatus,
			String message) {
		return Mono.fromSupplier(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("code", 1);
			body.put("message", message);
			return new UpstreamCallbackResponse(httpStatus, Map.of(), body);
		});
	}

	private String convertOs(String os) {
		if (os == null || os.isBlank()) {
			return "";
		}
		return switch (os.toLowerCase(Locale.ROOT)) {
			case "0", "android" -> "android";
			case "1", "ios" -> "ios";
			default -> "unknown";
		};
	}

	private String encode(String value) {
		return value == null ? "" : UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
	}

	private Set<String> requiredMacros(RouteSnapshot route) {
		return route.upstreamRequiredMacros() == null ? Set.of()
				: Arrays.stream(route.upstreamRequiredMacros().split("[,\\s]+"))
					.filter(value -> !value.isBlank())
					.collect(Collectors.toSet());
	}

	private boolean hasMacroValue(String macro, FeedbackReqV1 feedback) {
		return switch (macro) {
			case "__CALLBACK__" -> true;
			case "__IDFA__" -> hasText(feedback.getIdfa());
			case "__IDFA_MD5__" -> hasText(feedback.getIdfaMd5());
			case "__IMEI__" -> hasText(feedback.getImei());
			case "__IMEI_MD5__" -> hasText(feedback.getImeiMd5());
			case "__OAID__" -> hasText(feedback.getOaid());
			case "__OAID_MD5__" -> hasText(feedback.getOaidMd5());
			case "__CAID__", "__CAID_LIST__" -> hasText(feedback.getCaid());
			case "__ANDROIDID__", "__ANDROID_ID__" -> hasText(feedback.getAndroidId());
			case "__ANDROIDID_MD5__", "__ANDROID_ID_MD5__" -> hasText(feedback.getAndroidIdMd5());
			case "__AAID__" -> hasText(feedback.getAaid());
			case "__IP__" -> hasText(feedback.getIp());
			case "__UA__" -> hasText(feedback.getUa());
			case "__OS__" -> hasText(feedback.getOs());
			case "__MODEL__" -> hasText(feedback.getModel());
			case "__CLICKID__" -> hasText(feedback.getClickid());
			case "__CAMPAIGNID__" -> hasText(feedback.getCampaignId());
			case "__ADGROUPID__" -> hasText(feedback.getAdgroupId());
			case "__CREATIVEID__" -> hasText(feedback.getCreativeId());
			case "__ADVERTISERID__" -> hasText(feedback.getAdvertiserId());
			default -> false;
		};
	}

	private boolean isExplicitIos(FeedbackReqV1 feedback) {
		return "ios".equalsIgnoreCase(feedback.getOs()) || "1".equals(feedback.getOs());
	}

	private String businessCode(String body) {
		JsonNode json = parseJson(body);
		JsonNode code = json == null ? null : json.get("code");
		return code == null || code.isNull() ? null : code.asText();
	}

	private String businessMessage(String body) {
		JsonNode json = parseJson(body);
		if (json == null) {
			return null;
		}
		JsonNode message = json.has("message") ? json.get("message") : json.get("msg");
		return message == null || message.isNull() ? null : message.asText().replaceAll("[\\r\\n\\t]", " ");
	}

	private JsonNode parseJson(String body) {
		if (!hasText(body)) {
			return null;
		}
		try {
			JsonNode json = JSON.readTree(body);
			return json != null && json.isObject() ? json : null;
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private String firstNonBlank(String... values) {
		return Arrays.stream(values).filter(this::hasText).findFirst().orElse(null);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String truncate(String value, int length) {
		return value.length() <= length ? value : value.substring(0, length);
	}

	private void validateLength(String name, String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new IllegalArgumentException(name + " is too long");
		}
	}

}
