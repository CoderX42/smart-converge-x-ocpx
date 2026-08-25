package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.alibaba.fastjson2.JSON;
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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 微风互动 v1.3 点击监测 GET 投递实现。 */
@Component
public class WeifengUpstreamDeliveryService extends UpstreamDeliveryService {

	private static final Set<String> DEVICE_QUERY_PARAMETERS = Set.of("caid1", "caid2", "idfa", "oaid", "imei",
			"androidid", "ip", "ipv6", "ua", "ts", "osv", "model", "callback");

	@Value("${ocpx.weifeng.callback-base-url:${domain.name}}")
	private String callbackBaseUrl;

	@Value("${app.callback-crypto.keys.v1}")
	private String callbackAesKey;

	@Autowired(required = false)
	private OcpxKeyRingCrypto callbackCrypto;

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final WeifengEndpointPolicy endpointPolicy;

	private final WeifengHttpClient httpClient;

	private final OcpxFlowLogWriter flowLogWriter;

	private final MacroResolutionService macroResolutionService;

	@Autowired
	public WeifengUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			WeifengEndpointPolicy endpointPolicy, WeifengHttpClient httpClient, OcpxFlowLogWriter flowLogWriter,
			MacroResolutionService macroResolutionService) {
		this.promotionLinkIndex = promotionLinkIndex;
		this.endpointPolicy = endpointPolicy;
		this.httpClient = httpClient;
		this.flowLogWriter = flowLogWriter;
		this.macroResolutionService = macroResolutionService;
	}

	public WeifengUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			WeifengEndpointPolicy endpointPolicy, WeifengHttpClient httpClient, OcpxFlowLogWriter flowLogWriter) {
		this(promotionLinkIndex, endpointPolicy, httpClient, flowLogWriter, new MacroResolutionService());
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
			return;
		}
		String requestUrl = null;
		try {
			RequestBuildResult requestBuild = buildRequest(feedback);
			if (!requestBuild.success()) {
				flowLogWriter.upstreamDelivery(feedback, false, null, requestBuild.rejectionCode(), 0L);
				return;
			}
			requestUrl = requestBuild.requestUrl();
			requestUrl = endpointPolicy.requireAllowedClickUrl(requestUrl);
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			Integer status = result.statusCode();
			boolean success = status != null && status == 200;
			String conclusion = conclusion(status, result.errorCode());
			flowLogWriter.upstreamDelivery(feedback, success, status, conclusion, result.durationMs(), result.body(),
					requestUrl, macroAudit(feedback, requestUrl));
		}
		catch (IllegalArgumentException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "WEIFENG_URL_REJECTED", 0L, null, requestUrl,
					macroAudit(feedback, null));
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "WEIFENG_INTERNAL_ERROR", 0L, null, requestUrl,
					macroAudit(feedback, requestUrl));
			throw exception;
		}
	}

	private Map<String, String> macroAudit(FeedbackReq feedback, String requestUrl) {
		OcpxPromotionLink link = feedback == null ? null : promotionLinkIndex.get(feedback.getLId());
		return macroResolutionService.auditTemplate(link == null ? null : link.getLinkUrl(), requestUrl);
	}

	/** 使用上游推广链接中的固定参数替换设备与 Callback 宏，每个值仅编码一次。 */
	public String buildRequestUrl(FeedbackReq feedback) {
		return buildRequest(feedback).requestUrl();
	}

	private RequestBuildResult buildRequest(FeedbackReq feedback) {
		if (feedback == null || feedback.getOs() == null || feedback.getOs() == OsTypeEnum.UNKNOWN) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_INVALID_OS");
		}
		OcpxPromotionLink promotionLink = promotionLinkIndex.get(feedback.getLId());
		if (promotionLink == null) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_LINK_NOT_FOUND");
		}
		if (!StringUtils.hasText(promotionLink.getLinkUrl())) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_LINK_URL_MISSING");
		}
		if (!StringUtils.hasText(feedback.getModel())) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_MODEL_MISSING");
		}
		String timestamp = timestampMillis(feedback);
		if (!StringUtils.hasText(timestamp)) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_TIMESTAMP_INVALID");
		}
		String callback = buildCallbackUrl(feedback);
		if (!StringUtils.hasText(callback)) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_CALLBACK_BUILD_FAILED");
		}
		if (!macroResolutionService.hasInboundIdentity(feedback)) {
			return RequestBuildResult.rejected("WEIFENG_REQUEST_IDENTITY_MISSING");
		}

		Map<String, String> macros = new LinkedHashMap<>();
		macros.put("__CAID1__", firstText(feedback.getCaid1(), feedback.getCaid()));
		macros.put("__CAID2__", firstText(feedback.getCaid2(), feedback.getLastCaid(), feedback.getPrevCaid()));
		macros.put("__IDFA__", feedback.getIdfa());
		macros.put("__OAID__", feedback.getOaid());
		macros.put("__IMEI__", feedback.getImei());
		macros.put("__ANDROID_ID__", feedback.getAndroidid());
		macros.put("__ANDROIDID__", feedback.getAndroidid());
		macros.put("__IP__", feedback.getIp());
		macros.put("__IPV6__", feedback.getIpv6());
		macros.put("__UA__", feedback.getUa());
		macros.put("__TS__", timestamp);
		macros.put("__OS__", feedback.getOs().getIndex());
		macros.put("__OSV__", feedback.getOsv());
		macros.put("__MODEL__", feedback.getModel());
		macros.put("__CALLBACK__", callback);
		return expandPromotionLink(promotionLink.getLinkUrl(), macros, feedback.getOs());
	}

	private RequestBuildResult expandPromotionLink(String template, Map<String, String> macros, OsTypeEnum os) {
		String allowedTemplate = endpointPolicy.requireAllowedClickUrl(template);
		try {
			URI uri = URI.create(allowedTemplate);
			String rawQuery = uri.getRawQuery();
			if (!StringUtils.hasText(rawQuery)) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_QUERY_MISSING");
			}

			Map<String, String> expandedParameters = new LinkedHashMap<>();
			Set<String> parameterNames = new HashSet<>();
			boolean callbackMacroReplaced = false;
			for (String segment : rawQuery.split("&", -1)) {
				int separator = segment.indexOf('=');
				if (separator <= 0) {
					return RequestBuildResult.rejected("WEIFENG_TEMPLATE_PARAMETER_INVALID");
				}
				String name = URLDecoder.decode(segment.substring(0, separator), StandardCharsets.UTF_8).trim();
				String configuredValue = URLDecoder.decode(segment.substring(separator + 1), StandardCharsets.UTF_8);
				String normalizedName = name.toLowerCase(Locale.ROOT);
				if (!StringUtils.hasText(name)) {
					return RequestBuildResult.rejected("WEIFENG_TEMPLATE_PARAMETER_INVALID");
				}
				if (!parameterNames.add(normalizedName)) {
					return RequestBuildResult.rejected("WEIFENG_TEMPLATE_PARAMETER_DUPLICATE");
				}

				String expandedValue = configuredValue;
				if (macros.containsKey(configuredValue)) {
					expandedValue = macros.get(configuredValue);
					callbackMacroReplaced |= "callback".equals(normalizedName) && "__CALLBACK__".equals(configuredValue)
							&& StringUtils.hasText(expandedValue);
				}
				else if (containsUnresolvedMacro(configuredValue)) {
					return RequestBuildResult.rejected("WEIFENG_TEMPLATE_MACRO_UNRESOLVED");
				}
				else if (DEVICE_QUERY_PARAMETERS.contains(normalizedName)) {
					return RequestBuildResult.rejected("WEIFENG_TEMPLATE_DEVICE_PARAMETER_LITERAL");
				}
				if (StringUtils.hasText(expandedValue)) {
					expandedParameters.put(normalizedName, expandedValue);
				}
			}

			if (!StringUtils.hasText(expandedParameters.get("aid"))) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_AID_MISSING");
			}
			if (!StringUtils.hasText(expandedParameters.get("did"))) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_DID_MISSING");
			}
			if (!callbackMacroReplaced) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_CALLBACK_MISSING");
			}
			if (!StringUtils.hasText(expandedParameters.get("ts"))) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_TIMESTAMP_MISSING");
			}
			if (!StringUtils.hasText(expandedParameters.get("model"))) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_MODEL_MISSING");
			}
			if (!os.getIndex().equalsIgnoreCase(expandedParameters.get("os"))) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_OS_MISMATCH");
			}
			if (!hasExpandedAttributionIdentity(os, expandedParameters)) {
				return RequestBuildResult.rejected("WEIFENG_TEMPLATE_IDENTITY_MISSING");
			}

			StringBuilder requestUrl = new StringBuilder(allowedTemplate.substring(0, allowedTemplate.indexOf('?')));
			String separator = "?";
			for (Map.Entry<String, String> entry : expandedParameters.entrySet()) {
				requestUrl.append(separator)
					.append(NetUtil.strictUrlEncode(entry.getKey(), StandardCharsets.UTF_8))
					.append('=')
					.append(NetUtil.strictUrlEncode(entry.getValue(), StandardCharsets.UTF_8));
				separator = "&";
			}
			return RequestBuildResult.success(requestUrl.toString());
		}
		catch (IllegalArgumentException exception) {
			return RequestBuildResult.rejected("WEIFENG_TEMPLATE_INVALID");
		}
	}

	private record RequestBuildResult(String requestUrl, String rejectionCode) {

		private static RequestBuildResult success(String requestUrl) {
			return new RequestBuildResult(requestUrl, null);
		}

		private static RequestBuildResult rejected(String rejectionCode) {
			return new RequestBuildResult(null, rejectionCode);
		}

		private boolean success() {
			return StringUtils.hasText(requestUrl);
		}

	}

	private boolean hasExpandedAttributionIdentity(OsTypeEnum os, Map<String, String> parameters) {
		boolean ipAndUa = StringUtils.hasText(parameters.get("ip")) && StringUtils.hasText(parameters.get("ua"));
		if (os == OsTypeEnum.IOS) {
			return StringUtils.hasText(parameters.get("caid1")) || StringUtils.hasText(parameters.get("caid2"))
					|| StringUtils.hasText(parameters.get("idfa")) || ipAndUa;
		}
		return StringUtils.hasText(parameters.get("oaid")) || StringUtils.hasText(parameters.get("imei")) || ipAndUa;
	}

	private boolean containsUnresolvedMacro(String value) {
		return StringUtils.hasText(value) && value.matches(".*__[A-Za-z0-9_]+__.*");
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		if (feedback == null || !StringUtils.hasText(callbackBaseUrl)) {
			return null;
		}
		String callbackExt = callbackCrypto == null ? AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey)
				: callbackCrypto.encryptCallback(JSON.toJSONString(feedback));
		if (!StringUtils.hasText(callbackExt)) {
			return null;
		}
		String state = feedback.getLId() + "_" + feedback.getTId();
		String kid = callbackCrypto == null ? "" : "&kid=" + callbackCrypto.activeKeyId();
		return callbackBaseUrl + "/weifeng/callback?ext=" + callbackExt + kid + "&s=" + state
				+ "&event=__EVENT__&amount=__AMOUNT__&days=__DAYS__";
	}

	private String timestampMillis(FeedbackReq feedback) {
		String value = firstText(feedback.getTsMs(), feedback.getTs());
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

	private String firstText(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}

	private String conclusion(Integer status, String errorCode) {
		if (status == null) {
			return errorCode == null ? "WEIFENG_HTTP_FAILED" : "WEIFENG_" + errorCode;
		}
		return switch (status) {
			case 200 -> "WEIFENG_DELIVERED";
			case 402 -> "WEIFENG_CAMPAIGN_PAUSED";
			case 500 -> "WEIFENG_SERVER_ERROR";
			case 501 -> "WEIFENG_DUPLICATE_CLICK";
			default -> "WEIFENG_HTTP_FAILED";
		};
	}

}
