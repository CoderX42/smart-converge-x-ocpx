package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.delivery.MacroResolutionService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** AERIM 点击监测 GET 投递实现。 */
@Component
public class AerimUpstreamDeliveryService extends UpstreamDeliveryService {

	private static final Pattern RESIDUAL_MACRO = Pattern.compile("__[A-Z0-9_]+__");

	@Value("${ocpx.aerim.callback-base-url:${domain.name}}")
	private String callbackBaseUrl;

	@Value("${app.callback-crypto.keys.v1}")
	private String callbackAesKey;

	@Autowired(required = false)
	private OcpxKeyRingCrypto callbackCrypto;

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final AerimEndpointPolicy endpointPolicy;

	private final AerimHttpClient httpClient;

	private final OcpxFlowLogWriter flowLogWriter;

	private final MacroResolutionService macroResolutionService;

	@Autowired
	public AerimUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex, AerimEndpointPolicy endpointPolicy,
			AerimHttpClient httpClient, OcpxFlowLogWriter flowLogWriter,
			MacroResolutionService macroResolutionService) {
		this.promotionLinkIndex = promotionLinkIndex;
		this.endpointPolicy = endpointPolicy;
		this.httpClient = httpClient;
		this.flowLogWriter = flowLogWriter;
		this.macroResolutionService = macroResolutionService;
	}

	public AerimUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex, AerimEndpointPolicy endpointPolicy,
			AerimHttpClient httpClient, OcpxFlowLogWriter flowLogWriter) {
		this(promotionLinkIndex, endpointPolicy, httpClient, flowLogWriter, new MacroResolutionService());
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
			return;
		}
		String requestUrl = null;
		try {
			requestUrl = buildRequestUrl(feedback);
			if (requestUrl == null) {
				flowLogWriter.upstreamDelivery(feedback, false, null, "AERIM_REQUEST_REJECTED", 0L);
				return;
			}
			requestUrl = endpointPolicy.requireAllowedHttpUrl(requestUrl);
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			boolean dailyLimit = result.statusCode() != null && result.statusCode() == 200
					&& isDailyLimit(result.body());
			boolean success = result.statusCode() != null && result.statusCode() == 200 && !dailyLimit;
			String conclusion = dailyLimit ? "AERIM_DAILY_LIMIT" : success ? "AERIM_DELIVERED"
					: result.errorCode() == null ? "AERIM_HTTP_FAILED" : "AERIM_" + result.errorCode();
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), requestUrl, macroAudit(feedback, requestUrl));
		}
		catch (IllegalArgumentException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "AERIM_URL_REJECTED", 0L, null, requestUrl,
					macroAudit(feedback, null));
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "AERIM_INTERNAL_ERROR", 0L, null, requestUrl,
					macroAudit(feedback, requestUrl));
			throw exception;
		}
	}

	private Map<String, String> macroAudit(FeedbackReq feedback, String requestUrl) {
		OcpxPromotionLink link = feedback == null ? null : promotionLinkIndex.get(feedback.getLId());
		return macroResolutionService.auditTemplate(link == null ? null : link.getLinkUrl(), requestUrl);
	}

	/** 构造不带签名、缺失宏参数已移除的 AERIM 正式请求地址。 */
	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null) {
			return null;
		}
		if (!macroResolutionService.hasInboundIdentity(feedback)) {
			return null;
		}
		OcpxPromotionLink promotionLink = promotionLinkIndex.get(feedback.getLId());
		if (promotionLink == null || !feedback.getAId().equals(promotionLink.getAdvertiserId())
				|| !StringUtils.hasText(promotionLink.getLinkUrl())) {
			return null;
		}
		String template = promotionLink.getLinkUrl().trim();
		if (!template.contains("__CALLBACK__") && !template.contains("__CALLBACK_URL__")) {
			return null;
		}
		String callback = buildCallbackUrl(feedback);
		if (!StringUtils.hasText(callback)) {
			return null;
		}

		Map<String, String> macros = macros(feedback, callback);
		int fragmentIndex = template.indexOf('#');
		String withoutFragment = fragmentIndex < 0 ? template : template.substring(0, fragmentIndex);
		int queryIndex = withoutFragment.indexOf('?');
		String path = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
		if (RESIDUAL_MACRO.matcher(path).find()) {
			return null;
		}

		List<String> queryPairs = new ArrayList<>();
		if (queryIndex >= 0 && queryIndex < withoutFragment.length() - 1) {
			for (String pair : withoutFragment.substring(queryIndex + 1).split("&")) {
				String replaced = replaceQueryPair(pair, macros);
				if (replaced != null && !replaced.isBlank()) {
					queryPairs.add(replaced);
				}
			}
		}
		String requestUrl = path + (queryPairs.isEmpty() ? "" : "?" + String.join("&", queryPairs));
		if (RESIDUAL_MACRO.matcher(requestUrl).find() || !hasCallbackParameter(requestUrl)) {
			return null;
		}
		if (!macroResolutionService.hasForwardedIdentity(requestUrl, feedback.getOs())) {
			return null;
		}
		return requestUrl;
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		String state = feedback.getLId() + "_" + feedback.getTId();
		String callbackExt = callbackCrypto == null ? AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey)
				: callbackCrypto.encryptCallback(JSON.toJSONString(feedback));
		String kid = callbackCrypto == null ? "" : "&kid=" + callbackCrypto.activeKeyId();
		return callbackExt == null ? null
				: callbackBaseUrl + "/aerim/callback?ext=" + callbackExt + kid + "&s=" + state;
	}

	private Map<String, String> macros(FeedbackReq feedback, String callback) {
		Map<String, String> values = new LinkedHashMap<>();
		put(values, "__REQUEST_ID__", feedback.getReqId());
		put(values, "__CLICKID__",
				StringUtils.hasText(feedback.getClickId()) ? feedback.getClickId() : feedback.getReqId());
		put(values, "__CLICK_ID__",
				StringUtils.hasText(feedback.getClickId()) ? feedback.getClickId() : feedback.getReqId());
		put(values, "__IMEI__", feedback.getImei());
		put(values, "__IMEI_MD5__", feedback.getImeiMd5());
		put(values, "__IMEIMD5__", feedback.getImeiMd5());
		put(values, "__OAID__", feedback.getOaid());
		put(values, "__OAID_MD5__", feedback.getOaidMd5());
		put(values, "__OAIDMD5__", feedback.getOaidMd5());
		put(values, "__IDFA__", feedback.getIdfa());
		put(values, "__IDFA_MD5__", feedback.getIdfaMd5());
		put(values, "__CAID__", feedback.getCaid());
		put(values, "__CAID_LIST__",
				StringUtils.hasText(feedback.getCaidList()) ? feedback.getCaidList() : feedback.getCaid());
		put(values, "__ANDROIDID__", feedback.getAndroidid());
		put(values, "__ANDROIDID_MD5__", feedback.getAndroididMd5());
		put(values, "__ANDROIDMD5__", feedback.getAndroididMd5());
		put(values, "__AAID__", feedback.getAaid());
		put(values, "__SID__", feedback.getUserId());
		put(values, "__SIDMD5__", md5(feedback.getUserId()));
		put(values, "__IP__", feedback.getIp());
		put(values, "__IPV4__", feedback.getIp());
		put(values, "__IPV6__", feedback.getIpv6());
		put(values, "__IPMD5__", md5(feedback.getIp()));
		put(values, "__UA__", feedback.getUa());
		put(values, "__OS__", matchOs(feedback.getOs()));
		put(values, "__OS_TYPE__", matchOs(feedback.getOs()));
		put(values, "__TS__", feedback.getTs());
		put(values, "__TSMS__", timestampMillis(feedback));
		put(values, "__MODEL__", feedback.getModel());
		put(values, "__CALLBACK__", callback);
		put(values, "__CALLBACK_URL__", callback);
		put(values, "__CAMPAIGNID__", feedback.getPlanId());
		put(values, "__PLAN__", feedback.getPlanId());
		put(values, "__PLAN_ID__", feedback.getPlanId());
		put(values, "__ADGROUPID__", feedback.getGroupId());
		put(values, "__UNIT__", feedback.getGroupId());
		put(values, "__CREATIVEID__", feedback.getCreativeId());
		put(values, "__CID__", feedback.getCreativeId());
		put(values, "__IDEA_ID__", feedback.getCreativeId());
		put(values, "__ADVERTISERID__", feedback.getAdvId());
		put(values, "__UID__", feedback.getAdvId());
		put(values, "__USER_ID__", feedback.getAdvId());
		return values;
	}

	private void put(Map<String, String> values, String macro, String value) {
		values.put(macro, StringUtils.hasText(value) && !RESIDUAL_MACRO.matcher(value).find()
				? NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8) : null);
	}

	private String replaceQueryPair(String pair, Map<String, String> macros) {
		if (RESIDUAL_MACRO.matcher(pair).replaceAll("").contains("__")) {
			throw new IllegalArgumentException("malformed AERIM macro");
		}
		String value = pair;
		var matcher = RESIDUAL_MACRO.matcher(value);
		while (matcher.find()) {
			String macro = matcher.group();
			if (!macros.containsKey(macro)) {
				throw new IllegalArgumentException("unsupported AERIM macro");
			}
			String replacement = macros.get(macro);
			if (replacement == null) {
				return null;
			}
			value = value.replace(macro, replacement);
			matcher = RESIDUAL_MACRO.matcher(value);
		}
		return value;
	}

	private String timestampMillis(FeedbackReq feedback) {
		String value = StringUtils.hasText(feedback.getTsMs()) ? feedback.getTsMs() : feedback.getTs();
		if (!StringUtils.hasText(value) || !value.chars().allMatch(Character::isDigit)) {
			return null;
		}
		if (value.length() == 13) {
			return value;
		}
		if (value.length() == 10) {
			try {
				return String.valueOf(Math.multiplyExact(Long.parseLong(value), 1000L));
			}
			catch (ArithmeticException exception) {
				return null;
			}
		}
		return null;
	}

	private String md5(String value) {
		return StringUtils.hasText(value) ? DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8)) : null;
	}

	private boolean hasCallbackParameter(String url) {
		int queryIndex = url.indexOf('?');
		if (queryIndex < 0) {
			return false;
		}
		for (String pair : url.substring(queryIndex + 1).split("&")) {
			int separator = pair.indexOf('=');
			String name = separator > 0 ? pair.substring(0, separator).toLowerCase() : "";
			if (("callback".equals(name) || "callback_url".equals(name)) && separator < pair.length() - 1) {
				return true;
			}
		}
		return false;
	}

	private String matchOs(OsTypeEnum os) {
		return Objects.equals(os, OsTypeEnum.IOS) ? "ios" : "android";
	}

	private boolean isDailyLimit(String body) {
		if (!StringUtils.hasText(body)) {
			return false;
		}
		try {
			JSONObject payload = JSON.parseObject(body);
			return "402".equals(payload.getString("code"));
		}
		catch (RuntimeException ignored) {
			return false;
		}
	}

}
