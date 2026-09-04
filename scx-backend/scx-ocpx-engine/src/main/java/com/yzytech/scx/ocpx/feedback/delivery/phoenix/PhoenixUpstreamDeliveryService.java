package com.yzytech.scx.ocpx.feedback.delivery.phoenix;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.MacroResolutionService;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.PhoenixTemplateContract;
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
import java.util.Set;
import java.util.regex.Pattern;

/** 凤凰新闻 CPA 曝光/点击 GET 投递实现。 */
@Component
public class PhoenixUpstreamDeliveryService extends UpstreamDeliveryService {

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final PhoenixEndpointPolicy endpointPolicy;

	private final PhoenixHttpClient httpClient;

	private final OcpxKeyRingCrypto callbackCrypto;

	private final OcpxFlowLogWriter flowLogWriter;

	private final MacroResolutionService macroResolutionService;

	private final String callbackBaseUrl;

	private static final Pattern CALLBACK_KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");

	private static final Set<String> REQUIRED_PARAMETERS = Set.of("reqid", "os", "ts");

	public PhoenixUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			PhoenixEndpointPolicy endpointPolicy, PhoenixHttpClient httpClient, OcpxKeyRingCrypto callbackCrypto,
			OcpxFlowLogWriter flowLogWriter, MacroResolutionService macroResolutionService,
			@Value("${domain.name}") String callbackBaseUrl) {
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
		if (feedback == null || (!EventTypeEnum.IMP.equals(feedback.getEvent())
				&& !EventTypeEnum.CLICK.equals(feedback.getEvent()))) {
			return;
		}
		String requestUrl = null;
		try {
			requestUrl = endpointPolicy.requireAllowedHttpsUrl(buildRequestUrl(feedback));
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			Integer code = businessCode(result.body());
			boolean success = Integer.valueOf(200).equals(result.statusCode())
					&& (Integer.valueOf(0).equals(code) || (EventTypeEnum.IMP.equals(feedback.getEvent()) && code == null));
			String conclusion = EventTypeEnum.IMP.equals(feedback.getEvent()) && code == null ? "PHOENIX_DELIVERED"
					: conclusion(result.statusCode(), code, result.errorCode());
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), requestUrl, macroAudit(feedback, requestUrl));
		}
		catch (IllegalArgumentException exception) {
			String reason = exception.getMessage();
			String conclusion = reason != null && reason.startsWith("PHOENIX_") ? reason : "PHOENIX_REQUEST_REJECTED";
			flowLogWriter.upstreamDelivery(feedback, false, null, conclusion, 0L, null, requestUrl,
					macroAudit(feedback, null));
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "PHOENIX_INTERNAL_ERROR", 0L, null, requestUrl,
					macroAudit(feedback, requestUrl));
			throw exception;
		}
	}

	/** 构造凤凰曝光或点击地址；事件类型决定出站路径及是否携带 callback。 */
	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null) {
			throw new IllegalArgumentException("PHOENIX_FEEDBACK_MISSING");
		}
		boolean click = EventTypeEnum.CLICK.equals(feedback.getEvent());
		boolean impression = EventTypeEnum.IMP.equals(feedback.getEvent());
		if (!click && !impression) {
			throw new IllegalArgumentException("PHOENIX_EVENT_INVALID");
		}
		OcpxPromotionLink link = promotionLinkIndex.get(feedback.getLId());
		if (link == null || !StringUtils.hasText(feedback.getAId()) || !feedback.getAId().equals(link.getAdvertiserId())
				|| !StringUtils.hasText(link.getLinkUrl())) {
			throw new IllegalArgumentException("PHOENIX_PROMOTION_LINK_INVALID");
		}
		PhoenixTemplateContract.Template template = PhoenixTemplateContract.requireValid(link.getLinkUrl(), link.getPlatform(), true);
		OsTypeEnum os = requireOs(feedback, link.getPlatform());
		if (!StringUtils.hasText(feedback.getReqId()) || feedback.getReqId().length() > 128) {
			throw new IllegalArgumentException("PHOENIX_REQUEST_ID_INVALID");
		}
		String timestamp = timestampMillis(feedback);
		String oaid = null;
		String oaidMd5 = null;
		if (os == OsTypeEnum.ANDROID) {
			oaid = requireText(feedback.getOaid(), "PHOENIX_OAID_MISSING");
			oaidMd5 = digestValue(feedback.getOaidMd5(), oaid, "PHOENIX_OAID_MD5");
		}
		String caid = null;
		if (os == OsTypeEnum.IOS) {
			caid = caidValue(feedback.getCaidList(), feedback.getCaid());
			if (!StringUtils.hasText(caid)) {
				throw new IllegalArgumentException("PHOENIX_IOS_IDENTITY_MISSING");
			}
		}
		String idfa = normalizeIdfa(feedback.getIdfa());

		Map<String, String> macros = new LinkedHashMap<>();
		macros.put("__REQID__", feedback.getReqId());
		macros.put("__TAGID__", feedback.getTagId());
		macros.put("__OS__", os == OsTypeEnum.ANDROID ? "0" : "1");
		macros.put("__IP__", feedback.getIp());
		macros.put("__UA__", feedback.getUa());
		macros.put("__MODEL__", feedback.getModel());
		macros.put("__TS__", timestamp);
		macros.put("__IDFA__", idfa);
		macros.put("__IDFAMD5__", digestValue(feedback.getIdfaMd5(), idfa, "PHOENIX_IDFA_MD5"));
		macros.put("__CAID__", caid);
		macros.put("__IMEI__", feedback.getImei());
		macros.put("__OAID__", oaid);
		macros.put("__OAIDMD5__", oaidMd5);
		macros.put("__ANDROID__", feedback.getAndroidid());
		macros.put("__CALLBACK__", click ? buildCallbackUrl(feedback) : null);

		List<String> query = template.parameters().stream().map(parameter -> encodePair(parameter, macros, click))
				.filter(StringUtils::hasText).toList();
		URI uri = template.uri();
		String path = pathForEvent(uri.getRawPath(), click);
		String base = uri.getScheme() + "://" + uri.getRawAuthority() + path;
		return base + "?" + String.join("&", query);
	}

	/**
	 * 点击保留合作方填写的路径；曝光继续使用 Phoenix 协议的曝光路径。
	 */
	private String pathForEvent(String rawPath, boolean click) {
		return click ? (StringUtils.hasText(rawPath) ? rawPath : "/") : PhoenixTemplateContract.IMPRESSION_PATH;
	}

	/** 生成带当前密钥版本的 Phoenix Callback 地址。 */
	public String buildCallbackUrl(FeedbackReq feedback) {
		if (!StringUtils.hasText(callbackBaseUrl)) {
			throw new IllegalArgumentException("PHOENIX_CALLBACK_BASE_URL_MISSING");
		}
		String base = callbackBaseUrl.trim().replaceAll("/+$", "");
		URI baseUri;
		try {
			baseUri = URI.create(base);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("PHOENIX_CALLBACK_BASE_URL_INVALID", exception);
		}
		if (!("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))
				|| !StringUtils.hasText(baseUri.getHost()) || baseUri.getUserInfo() != null
				|| baseUri.getQuery() != null || baseUri.getFragment() != null || !"/ocpx".equals(baseUri.getPath())) {
			throw new IllegalArgumentException("PHOENIX_CALLBACK_BASE_URL_INVALID");
		}
		String keyId = callbackCrypto.activeKeyId();
		if (!StringUtils.hasText(keyId) || !CALLBACK_KEY_ID.matcher(keyId).matches()) {
			throw new IllegalArgumentException("PHOENIX_CALLBACK_KEY_ID_INVALID");
		}
		String ext = callbackCrypto.encryptCallback(JSON.toJSONString(feedback));
		return base + "/phoenix/callback?kid=" + NetUtil.strictUrlEncode(keyId, StandardCharsets.UTF_8) + "&ext="
				+ NetUtil.strictUrlEncode(ext, StandardCharsets.UTF_8);
	}

	private String encodePair(PhoenixTemplateContract.QueryParameter parameter, Map<String, String> macros,
			boolean click) {
		if (!click && "callback".equalsIgnoreCase(parameter.name())) {
			return null;
		}
		String value = parameter.value();
		if (value.startsWith("__") && value.endsWith("__")) {
			value = macros.get(value.toUpperCase(Locale.ROOT));
			if (!StringUtils.hasText(value)) {
				if (REQUIRED_PARAMETERS.contains(parameter.name().toLowerCase(Locale.ROOT))) {
					throw new IllegalArgumentException("PHOENIX_TEMPLATE_VALUE_MISSING");
				}
				return null;
			}
		}
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("PHOENIX_TEMPLATE_VALUE_INVALID");
		}
		return NetUtil.strictUrlEncode(parameter.name(), StandardCharsets.UTF_8) + "="
				+ NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8);
	}

	private OsTypeEnum requireOs(FeedbackReq feedback, Integer platform) {
		OsTypeEnum os = feedback.getOs();
		if (os != OsTypeEnum.ANDROID && os != OsTypeEnum.IOS) {
			throw new IllegalArgumentException("PHOENIX_OS_INVALID");
		}
		if (Integer.valueOf(PhoenixTemplateContract.ANDROID_PLATFORM).equals(platform) && os != OsTypeEnum.ANDROID) {
			throw new IllegalArgumentException("PHOENIX_OS_INVALID");
		}
		if (Integer.valueOf(PhoenixTemplateContract.IOS_PLATFORM).equals(platform) && os != OsTypeEnum.IOS) {
			throw new IllegalArgumentException("PHOENIX_OS_INVALID");
		}
		if (!Integer.valueOf(PhoenixTemplateContract.ANDROID_PLATFORM).equals(platform)
				&& !Integer.valueOf(PhoenixTemplateContract.IOS_PLATFORM).equals(platform)
				&& !Integer.valueOf(PhoenixTemplateContract.ANDROID_IOS_PLATFORM).equals(platform)) {
			throw new IllegalArgumentException("PHOENIX_PROMOTION_LINK_INVALID");
		}
		return os;
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
			return errorCode == null ? "PHOENIX_HTTP_FAILED" : "PHOENIX_" + errorCode;
		}
		if (httpStatus != 200) {
			return switch (httpStatus) {
				case 404 -> "PHOENIX_HTTP_NOT_FOUND";
				case 500 -> "PHOENIX_HTTP_SERVER_ERROR";
				default -> "PHOENIX_HTTP_FAILED";
			};
		}
		if (businessCode == null) {
			return "PHOENIX_RESPONSE_INVALID";
		}
		return switch (businessCode) {
			case 0 -> "PHOENIX_DELIVERED";
			case 10001 -> "PHOENIX_OS_MISSING";
			case 10002 -> "PHOENIX_CALLBACK_MISSING";
			case 10003 -> "PHOENIX_OS_INVALID";
			case 10004 -> "PHOENIX_IOS_IDENTITY_MISSING";
			case 10005 -> "PHOENIX_ANDROID_IDENTITY_MISSING";
			case 10006 -> "PHOENIX_DELIVERY_STOPPED";
			case 10007 -> "PHOENIX_BALANCE_INSUFFICIENT";
			default -> "PHOENIX_BUSINESS_ERROR";
		};
	}

	private Map<String, String> macroAudit(FeedbackReq feedback, String requestUrl) {
		OcpxPromotionLink link = feedback == null ? null : promotionLinkIndex.get(feedback.getLId());
		return macroResolutionService.auditTemplate(link == null ? null : link.getLinkUrl(), requestUrl);
	}

	private String timestampMillis(FeedbackReq feedback) {
		String value = StringUtils.hasText(feedback.getTsMs()) ? feedback.getTsMs() : feedback.getTs();
		if (!StringUtils.hasText(value) || !value.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("PHOENIX_TIMESTAMP_INVALID");
		}
		try {
			if (value.length() == 13) {
				return value;
			}
			if (value.length() == 10) {
				return Long.toString(Math.multiplyExact(Long.parseLong(value), 1000L));
			}
		}
		catch (ArithmeticException | NumberFormatException exception) {
			// fall through to the stable protocol rejection below
		}
		throw new IllegalArgumentException("PHOENIX_TIMESTAMP_INVALID");
	}

	private String digestValue(String digest, String raw, String codePrefix) {
		if (StringUtils.hasText(digest)) {
			String normalized = digest.trim().toUpperCase(Locale.ROOT);
			if (!normalized.matches("[0-9A-F]{32}")) {
				throw new IllegalArgumentException(codePrefix + "_INVALID");
			}
			if (StringUtils.hasText(raw) && !DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8))
					.equalsIgnoreCase(normalized)) {
				throw new IllegalArgumentException(codePrefix + "_MISMATCH");
			}
			return normalized;
		}
		return StringUtils.hasText(raw)
				? DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)).toUpperCase(Locale.ROOT) : null;
	}

	private String caidValue(String first, String second) {
		if (StringUtils.hasText(first)) {
			return first.trim();
		}
		return StringUtils.hasText(second) ? second.trim() : null;
	}

	private String normalizeIdfa(String value) {
		return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
	}

	private String requireText(String value, String code) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(code);
		}
		return value.trim();
	}

}
