package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

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

	@Override
	public String code() {
		return "COCO";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("UPSTREAM", Set.of(OcpxEventType.CLICK), Set.of("GET"), Set.of("NONE"),
				Duration.ofSeconds(3),
				Set.of("__IDFA__", "__IDFA_MD5__", "__IMEI__", "__IMEI_MD5__", "__OAID__", "__OAID_MD5__", "__IP__",
						"__CAID_LIST__", "__ANDROID_ID__", "__ANDROID_ID_MD5__", "__AAID__", "__UA__", "__OS__",
						"__MODEL__", "__CALLBACK__", "__CLICKID__", "__CREATIVEID__"),
				true);
	}

	@Override
	public boolean supports(OcpxEventType eventType) {
		return eventType == OcpxEventType.CLICK;
	}

	@Override
	public AdapterHttpRequest buildFeedback(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl) {
		Map<String, String> macros = new LinkedHashMap<>();
		macros.put("__IDFA__", feedback.getIdfa());
		macros.put("__IDFA_MD5__", feedback.getIdfaMd5());
		macros.put("__IMEI__", feedback.getImei());
		macros.put("__IMEI_MD5__", feedback.getImeiMd5());
		macros.put("__OAID__", feedback.getOaid());
		macros.put("__OAID_MD5__", feedback.getOaidMd5());
		macros.put("__IP__", feedback.getIp());
		macros.put("__CAID_LIST__", feedback.getCaid());
		macros.put("__ANDROID_ID__", feedback.getAndroidId());
		macros.put("__ANDROID_ID_MD5__", feedback.getAndroidIdMd5());
		macros.put("__AAID__", feedback.getAaid());
		macros.put("__UA__", feedback.getUa());
		macros.put("__OS__", convertOs(feedback.getOs()));
		macros.put("__MODEL__", feedback.getModel());
		macros.put("__CALLBACK__", callbackUrl);
		macros.put("__CLICKID__", feedback.getClickid());
		macros.put("__CREATIVEID__", feedback.getCreativeId());
		Set<String> required = route.upstreamRequiredMacros() == null ? Set.of()
				: Arrays.stream(route.upstreamRequiredMacros().split("[,\\s]+"))
					.filter(value -> !value.isBlank())
					.collect(Collectors.toSet());
		String url = route.bidUrl();
		for (Map.Entry<String, String> entry : macros.entrySet()) {
			String replacement = entry.getValue() == null || entry.getValue().isBlank()
					? (required.contains(entry.getKey()) ? entry.getKey() : "") : encode(entry.getValue());
			url = url.replace(entry.getKey(), replacement);
		}
		if (url.matches(".*__[A-Z0-9_]+__.*")) {
			throw new IllegalArgumentException("Coco URL contains unresolved required macros");
		}
		return new AdapterHttpRequest("GET", URI.create(url), Map.of(), null);
	}

	@Override
	public boolean isSuccess(AdapterHttpResponse response) {
		return response.statusCode() >= 200 && response.statusCode() < 300;
	}

	@Override
	public NormalizedCallbackEvent parseCallback(String callbackRef, Map<String, String> parameters) {
		OcpxEventType eventType = OcpxEventType.fromExternal(parameters.get("action_type"));
		String amountRaw = parameters.get("currency_amount");
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
		String eventId = OcpxHashing.sha256(callbackRef + "|" + identityParameters);
		return new NormalizedCallbackEvent(eventId, null, callbackRef, eventType, OffsetDateTime.now(), amount,
				amountRaw);
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

}
