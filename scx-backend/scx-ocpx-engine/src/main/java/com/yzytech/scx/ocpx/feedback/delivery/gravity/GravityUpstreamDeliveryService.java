package com.yzytech.scx.ocpx.feedback.delivery.gravity;

import com.yzytech.scx.ocpx.callback.web.gravity.GravityCallbackContext;
import com.yzytech.scx.ocpx.callback.web.gravity.GravityCallbackContextCodec;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 引力 Android/iOS 点击监测 GET 投递实现。 */
@Component
public class GravityUpstreamDeliveryService extends UpstreamDeliveryService {

	private static final int ANDROID_IOS_PLATFORM = 3;

	private static final Set<String> DYNAMIC_PARAMETERS = Set.of("idfa", "oaid", "android_id", "imei", "ip", "ua",
			"callback");

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final GravityEndpointPolicy endpointPolicy;

	private final GravityHttpClient httpClient;

	private final GravityCallbackContextCodec contextCodec;

	private final OcpxFlowLogWriter flowLogWriter;

	private final String callbackBaseUrl;

	@Autowired(required = false)
	private WireAuditService wireAuditService;

	public GravityUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			GravityEndpointPolicy endpointPolicy, GravityHttpClient httpClient,
			GravityCallbackContextCodec contextCodec, OcpxFlowLogWriter flowLogWriter,
			@Value("${ocpx.gravity.callback-base-url:${domain.name}}") String callbackBaseUrl) {
		this.promotionLinkIndex = promotionLinkIndex;
		this.endpointPolicy = endpointPolicy;
		this.httpClient = httpClient;
		this.contextCodec = contextCodec;
		this.flowLogWriter = flowLogWriter;
		this.callbackBaseUrl = callbackBaseUrl;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
			return;
		}
		WireAuditService.Handle audit = null;
		String requestUrl = null;
		try {
			requestUrl = endpointPolicy.requireAllowedHttpsUrl(buildRequestUrl(feedback));
			audit = beginAudit(feedback, requestUrl, true);
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			boolean success = result.statusCode() != null && result.statusCode() >= 200 && result.statusCode() < 300;
			String conclusion = success ? "GRAVITY_HTTP_ACCEPTED"
					: result.errorCode() == null ? "GRAVITY_HTTP_FAILED" : "GRAVITY_" + result.errorCode();
			completeAudit(audit, result, conclusion);
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), requestUrl, macroAudit(feedback));
		}
		catch (IllegalArgumentException exception) {
			String conclusion = exception.getMessage() != null && exception.getMessage().startsWith("GRAVITY_")
					? exception.getMessage() : "GRAVITY_REQUEST_REJECTED";
			if (audit == null)
				audit = beginAudit(feedback, requestUrl, false);
			if (wireAuditService != null)
				wireAuditService.completeHttp(audit, null, Map.of(), new byte[0], conclusion, exception, null);
			flowLogWriter.upstreamDelivery(feedback, false, null, conclusion, 0L);
		}
		catch (RuntimeException exception) {
			if (audit == null)
				audit = beginAudit(feedback, requestUrl, false);
			if (wireAuditService != null)
				wireAuditService.completeHttp(audit, null, Map.of(), new byte[0], "GRAVITY_INTERNAL_ERROR", exception,
						null);
			flowLogWriter.upstreamDelivery(feedback, false, null, "GRAVITY_INTERNAL_ERROR", 0L);
			throw exception;
		}
	}

	private WireAuditService.Handle beginAudit(FeedbackReq feedback, String url, boolean sent) {
		return wireAuditService == null ? null
				: wireAuditService.beginOutbound("ADVERTISER_DELIVERY", "GRAVITY", "FORMAL", url, sent, feedback);
	}

	private void completeAudit(WireAuditService.Handle audit, NetUtil.HttpGetResult result, String conclusion) {
		if (wireAuditService != null)
			wireAuditService.completeOutbound(audit, result, conclusion);
	}

	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null) {
			throw new IllegalArgumentException("GRAVITY_FEEDBACK_MISSING");
		}
		OcpxPromotionLink link = promotionLinkIndex.get(feedback.getLId());
		if (link == null || !feedback.getAId().equals(link.getAdvertiserId())
				|| !Integer.valueOf(ANDROID_IOS_PLATFORM).equals(link.getPlatform())
				|| !StringUtils.hasText(link.getLinkUrl())) {
			throw new IllegalArgumentException("GRAVITY_PROMOTION_LINK_INVALID");
		}
		if (!validIpv4(feedback.getIp())) {
			throw new IllegalArgumentException("GRAVITY_IPV4_INVALID");
		}
		if (!StringUtils.hasText(feedback.getUa())) {
			throw new IllegalArgumentException("GRAVITY_UA_MISSING");
		}

		Map<String, String> dynamic = new LinkedHashMap<>();
		if (feedback.getOs() == OsTypeEnum.IOS) {
			if (!StringUtils.hasText(feedback.getIdfa())) {
				throw new IllegalArgumentException("GRAVITY_IDFA_MISSING");
			}
			dynamic.put("idfa", feedback.getIdfa());
		}
		else if (feedback.getOs() == OsTypeEnum.ANDROID) {
			put(dynamic, "oaid", feedback.getOaid());
			put(dynamic, "android_id", feedback.getAndroidid());
			put(dynamic, "imei", feedback.getImei());
			if (dynamic.isEmpty()) {
				throw new IllegalArgumentException("GRAVITY_ANDROID_ID_MISSING");
			}
		}
		else {
			throw new IllegalArgumentException("GRAVITY_OS_INVALID");
		}
		dynamic.put("ip", feedback.getIp());
		dynamic.put("ua", feedback.getUa());
		dynamic.put("callback", buildCallbackUrl(feedback));

		String baseUrl = link.getLinkUrl().trim();
		if (baseUrl.contains("{") || baseUrl.contains("}")) {
			throw new IllegalArgumentException("GRAVITY_TEMPLATE_MACRO_FORBIDDEN");
		}
		URI uri;
		try {
			uri = URI.create(baseUrl);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("GRAVITY_URL_UNSAFE", exception);
		}
		List<String> fixedPairs = fixedQueryPairs(uri.getRawQuery());
		String path = baseUrl.substring(0, baseUrl.indexOf('?') < 0 ? baseUrl.length() : baseUrl.indexOf('?'));
		List<String> pairs = new ArrayList<>(fixedPairs);
		dynamic
			.forEach((name, value) -> pairs.add(name + "=" + NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8)));
		return path + "?" + String.join("&", pairs);
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		if (!StringUtils.hasText(callbackBaseUrl)) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_BASE_URL_MISSING");
		}
		GravityCallbackContext context = new GravityCallbackContext(1, ActionTypeEnum.ACTIVATE.name(),
				feedback.getMId(), feedback.getTId(), feedback.getAId(), feedback.getLId(), feedback.getReqId(),
				feedback.getCallbackUrl());
		String base = callbackBaseUrl.endsWith("/") ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1)
				: callbackBaseUrl;
		return base + "/gravity/callback?ctx=" + contextCodec.encode(context) + "&kid=" + contextCodec.activeKeyId();
	}

	private List<String> fixedQueryPairs(String rawQuery) {
		if (!StringUtils.hasText(rawQuery)) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String pair : rawQuery.split("&")) {
			if (!StringUtils.hasText(pair)) {
				continue;
			}
			int separator = pair.indexOf('=');
			String rawName = separator < 0 ? pair : pair.substring(0, separator);
			String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
			if (DYNAMIC_PARAMETERS.contains(name)) {
				throw new IllegalArgumentException("GRAVITY_DYNAMIC_PARAMETER_FORBIDDEN");
			}
			result.add(pair);
		}
		return result;
	}

	private Map<String, Object> macroAudit(FeedbackReq feedback) {
		Map<String, Object> audit = new LinkedHashMap<>();
		audit.put("os", feedback.getOs());
		audit.put("idfa", StringUtils.hasText(feedback.getIdfa()));
		audit.put("oaid", StringUtils.hasText(feedback.getOaid()));
		audit.put("android_id", StringUtils.hasText(feedback.getAndroidid()));
		audit.put("imei", StringUtils.hasText(feedback.getImei()));
		audit.put("ip", StringUtils.hasText(feedback.getIp()));
		audit.put("ua", StringUtils.hasText(feedback.getUa()));
		audit.put("callback", true);
		return audit;
	}

	private void put(Map<String, String> values, String name, String value) {
		if (StringUtils.hasText(value)) {
			values.put(name, value);
		}
	}

	private boolean validIpv4(String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
				return false;
			}
			try {
				if (Integer.parseInt(part) > 255) {
					return false;
				}
			}
			catch (NumberFormatException exception) {
				return false;
			}
		}
		return true;
	}

}
