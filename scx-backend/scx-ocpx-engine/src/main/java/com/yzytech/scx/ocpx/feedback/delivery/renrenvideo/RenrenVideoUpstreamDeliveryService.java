package com.yzytech.scx.ocpx.feedback.delivery.renrenvideo;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.MacroResolutionService;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.RenrenVideoTemplateContract;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 人人视频 CPA 点击监测 GET 投递实现。 */
@Component
public class RenrenVideoUpstreamDeliveryService extends UpstreamDeliveryService {

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final RenrenVideoEndpointPolicy endpointPolicy;

	private final RenrenVideoHttpClient httpClient;

	private final OcpxKeyRingCrypto callbackCrypto;

	private final OcpxFlowLogWriter flowLogWriter;

	private final MacroResolutionService macroResolutionService;

	private final String callbackBaseUrl;

	public RenrenVideoUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			RenrenVideoEndpointPolicy endpointPolicy, RenrenVideoHttpClient httpClient,
			OcpxKeyRingCrypto callbackCrypto, OcpxFlowLogWriter flowLogWriter,
			MacroResolutionService macroResolutionService, @Value("${domain.name}") String callbackBaseUrl) {
		this.promotionLinkIndex = promotionLinkIndex;
		this.endpointPolicy = endpointPolicy;
		this.httpClient = httpClient;
		this.callbackCrypto = callbackCrypto;
		this.flowLogWriter = flowLogWriter;
		this.macroResolutionService = macroResolutionService;
		this.callbackBaseUrl = callbackBaseUrl;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
			return;
		}
		String requestUrl = null;
		try {
			requestUrl = endpointPolicy.requireAllowedHttpUrl(buildRequestUrl(feedback));
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			Integer businessCode = businessCode(result.body());
			boolean success = Integer.valueOf(200).equals(result.statusCode())
					&& Integer.valueOf(200).equals(businessCode);
			String conclusion = conclusion(result.statusCode(), businessCode, result.errorCode());
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), requestUrl, macroAudit(feedback, requestUrl));
		}
		catch (IllegalArgumentException exception) {
			String conclusion = exception.getMessage() != null && exception.getMessage().startsWith("RENREN_VIDEO_")
					? exception.getMessage() : "RENREN_VIDEO_REQUEST_REJECTED";
			flowLogWriter.upstreamDelivery(feedback, false, null, conclusion, 0L, null, requestUrl,
					macroAudit(feedback, null));
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "RENREN_VIDEO_INTERNAL_ERROR", 0L, null, requestUrl,
					macroAudit(feedback, requestUrl));
			throw exception;
		}
	}

	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null) {
			throw new IllegalArgumentException("RENREN_VIDEO_FEEDBACK_MISSING");
		}
		OcpxPromotionLink link = promotionLinkIndex.get(feedback.getLId());
		if (link == null || !StringUtils.hasText(feedback.getAId()) || !feedback.getAId().equals(link.getAdvertiserId())
				|| !StringUtils.hasText(link.getLinkUrl())) {
			throw new IllegalArgumentException("RENREN_VIDEO_PROMOTION_LINK_INVALID");
		}
		RenrenVideoTemplateContract.Template template = RenrenVideoTemplateContract.requireValid(link.getLinkUrl(),
				link.getPlatform());
		validatePlatform(feedback.getOs(), link.getPlatform());
		String millis = timestampMillis(feedback);
		String seconds = Long.toString(Long.parseLong(millis) / 1000L);
		if (!validIpv4(feedback.getIp())) {
			throw new IllegalArgumentException("RENREN_VIDEO_IPV4_INVALID");
		}
		if (!StringUtils.hasText(feedback.getUa())) {
			throw new IllegalArgumentException("RENREN_VIDEO_UA_MISSING");
		}
		if (!StringUtils.hasText(feedback.getReqId()) || feedback.getReqId().length() > 128) {
			throw new IllegalArgumentException("RENREN_VIDEO_REQUEST_ID_INVALID");
		}

		Map<String, String> macros = new LinkedHashMap<>();
		macros.put("__TS__", millis);
		macros.put("__TS_S__", seconds);
		macros.put("__IP__", feedback.getIp());
		macros.put("__OS__", feedback.getOs() == OsTypeEnum.IOS ? "ios" : "android");
		macros.put("__UA__", feedback.getUa());
		macros.put("__MODEL__", feedback.getModel());
		macros.put("__REQID__", feedback.getReqId());
		macros.put("__CLICK_ID__", firstText(feedback.getClickId(), feedback.getReqId() + "_" + millis));
		macros.put("__PASS_THROUGH__", link.getLinkExt());
		macros.put("__CALLBACK__", buildCallbackUrl(feedback));

		if (feedback.getOs() == OsTypeEnum.ANDROID) {
			macros.put("__IMEI__", md5Value(feedback.getImeiMd5(), feedback.getImei()));
			macros.put("__OAID__", feedback.getOaid());
			macros.put("__OAID_MD5__", md5Value(feedback.getOaidMd5(), feedback.getOaid()));
			macros.put("__ANDROID_ID__", feedback.getAndroidid());
			macros.put("__ANDROID_ID_MD5__", md5Value(feedback.getAndroididMd5(), feedback.getAndroidid()));
			requireText(macros.get("__IMEI__"), "RENREN_VIDEO_IMEI_MD5_MISSING");
			requireText(macros.get("__OAID__"), "RENREN_VIDEO_OAID_MISSING");
			requireText(macros.get("__OAID_MD5__"), "RENREN_VIDEO_OAID_MD5_MISSING");
		}
		else {
			macros.put("__CAID__", firstText(feedback.getCaidList(), feedback.getCaid()));
			macros.put("__IDFA__", feedback.getIdfa());
			macros.put("__IDFA_MD5__", md5Value(feedback.getIdfaMd5(), feedback.getIdfa()));
			if (!StringUtils.hasText(macros.get("__CAID__")) && !StringUtils.hasText(macros.get("__IDFA__"))
					&& !StringUtils.hasText(macros.get("__IDFA_MD5__"))) {
				throw new IllegalArgumentException("RENREN_VIDEO_IOS_IDENTITY_MISSING");
			}
		}

		List<String> query = template.parameters().stream().map(parameter -> encodePair(parameter, macros)).toList();
		URI uri = template.uri();
		String base = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
		return base + "?" + String.join("&", query);
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		if (!StringUtils.hasText(callbackBaseUrl)) {
			throw new IllegalArgumentException("RENREN_VIDEO_CALLBACK_BASE_URL_MISSING");
		}
		String base = callbackBaseUrl.endsWith("/") ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1)
				: callbackBaseUrl;
		String ext = callbackCrypto.encryptCallback(JSON.toJSONString(feedback));
		return base + "/renren-video/callback?kid=" + callbackCrypto.activeKeyId() + "&ext=" + ext;
	}

	private String encodePair(RenrenVideoTemplateContract.QueryParameter parameter, Map<String, String> macros) {
		String value = parameter.value();
		if (value.startsWith("__") && value.endsWith("__")) {
			value = macros.get(value);
			if (!StringUtils.hasText(value)) {
				throw new IllegalArgumentException("RENREN_VIDEO_TEMPLATE_VALUE_MISSING");
			}
		}
		return NetUtil.strictUrlEncode(parameter.name(), StandardCharsets.UTF_8) + "="
				+ NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8);
	}

	private void validatePlatform(OsTypeEnum os, Integer platform) {
		if (os == OsTypeEnum.ANDROID && Integer.valueOf(RenrenVideoTemplateContract.ANDROID_PLATFORM).equals(platform)
				|| os == OsTypeEnum.IOS && Integer.valueOf(RenrenVideoTemplateContract.IOS_PLATFORM).equals(platform)) {
			return;
		}
		throw new IllegalArgumentException("RENREN_VIDEO_OS_PLATFORM_MISMATCH");
	}

	private String timestampMillis(FeedbackReq feedback) {
		String value = firstText(feedback.getTsMs(), feedback.getTs());
		if (!StringUtils.hasText(value) || !value.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("RENREN_VIDEO_TIMESTAMP_INVALID");
		}
		if (value.length() == 13) {
			return value;
		}
		if (value.length() == 10) {
			try {
				return Long.toString(Math.multiplyExact(Long.parseLong(value), 1000L));
			}
			catch (ArithmeticException exception) {
				throw new IllegalArgumentException("RENREN_VIDEO_TIMESTAMP_INVALID", exception);
			}
		}
		throw new IllegalArgumentException("RENREN_VIDEO_TIMESTAMP_INVALID");
	}

	private Integer businessCode(String body) {
		if (!StringUtils.hasText(body)) {
			return null;
		}
		try {
			return JSON.parseObject(body).getInteger("code");
		}
		catch (RuntimeException ignored) {
			return null;
		}
	}

	String conclusion(Integer httpStatus, Integer businessCode, String errorCode) {
		if (httpStatus == null) {
			return errorCode == null ? "RENREN_VIDEO_HTTP_FAILED" : "RENREN_VIDEO_" + errorCode;
		}
		if (httpStatus != 200) {
			return switch (httpStatus) {
				case 404 -> "RENREN_VIDEO_HTTP_NOT_FOUND";
				case 500 -> "RENREN_VIDEO_HTTP_SERVER_ERROR";
				default -> "RENREN_VIDEO_HTTP_FAILED";
			};
		}
		if (businessCode == null) {
			return "RENREN_VIDEO_RESPONSE_INVALID";
		}
		return switch (businessCode) {
			case 200 -> "RENREN_VIDEO_DELIVERED";
			case 201 -> "RENREN_VIDEO_INVALID_USER_ID";
			case 202 -> "RENREN_VIDEO_BUDGET_EXHAUSTED";
			case 203 -> "RENREN_VIDEO_BALANCE_INSUFFICIENT";
			case 204 -> "RENREN_VIDEO_REQUIRED_PARAM_MISSING";
			case 205 -> "RENREN_VIDEO_PARAM_TOO_LONG";
			case 300 -> "RENREN_VIDEO_SYSTEM_ERROR";
			default -> "RENREN_VIDEO_BUSINESS_ERROR";
		};
	}

	private Map<String, String> macroAudit(FeedbackReq feedback, String requestUrl) {
		OcpxPromotionLink link = feedback == null ? null : promotionLinkIndex.get(feedback.getLId());
		return macroResolutionService.auditTemplate(link == null ? null : link.getLinkUrl(), requestUrl);
	}

	private String md5Value(String digest, String raw) {
		if (StringUtils.hasText(digest)) {
			return digest.toLowerCase(Locale.ROOT);
		}
		return StringUtils.hasText(raw)
				? DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)).toLowerCase(Locale.ROOT) : null;
	}

	private String firstText(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}

	private void requireText(String value, String code) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(code);
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
