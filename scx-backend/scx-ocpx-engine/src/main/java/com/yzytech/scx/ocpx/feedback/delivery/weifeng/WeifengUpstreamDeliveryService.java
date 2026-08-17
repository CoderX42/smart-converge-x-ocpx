package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.beans.factory.annotation.Value;
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

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final WeifengEndpointPolicy endpointPolicy;

	private final WeifengHttpClient httpClient;

	private final OcpxFlowLogWriter flowLogWriter;

	public WeifengUpstreamDeliveryService(OcpxPromotionLinkIndex promotionLinkIndex,
			WeifengEndpointPolicy endpointPolicy, WeifengHttpClient httpClient, OcpxFlowLogWriter flowLogWriter) {
		this.promotionLinkIndex = promotionLinkIndex;
		this.endpointPolicy = endpointPolicy;
		this.httpClient = httpClient;
		this.flowLogWriter = flowLogWriter;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
			return;
		}
		try {
			String requestUrl = buildRequestUrl(feedback);
			if (!StringUtils.hasText(requestUrl)) {
				flowLogWriter.upstreamDelivery(feedback, false, null, "WEIFENG_REQUEST_REJECTED", 0L);
				return;
			}
			requestUrl = endpointPolicy.requireAllowedClickUrl(requestUrl);
			NetUtil.HttpGetResult result = httpClient.get(requestUrl);
			Integer status = result.statusCode();
			boolean success = status != null && status == 200;
			String conclusion = conclusion(status, result.errorCode());
			flowLogWriter.upstreamDelivery(feedback, success, status, conclusion, result.durationMs());
		}
		catch (IllegalArgumentException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "WEIFENG_URL_REJECTED", 0L);
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "WEIFENG_INTERNAL_ERROR", 0L);
			throw exception;
		}
	}

	/** 使用上游推广链接中的固定参数替换设备与 Callback 宏，每个值仅编码一次。 */
	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null || feedback.getOs() == null || feedback.getOs() == OsTypeEnum.UNKNOWN) {
			return null;
		}
		OcpxPromotionLink promotionLink = promotionLinkIndex.get(feedback.getLId());
		String timestamp = timestampMillis(feedback);
		String callback = buildCallbackUrl(feedback);
		if (promotionLink == null || !StringUtils.hasText(promotionLink.getLinkUrl())
				|| !StringUtils.hasText(feedback.getModel())
				|| !StringUtils.hasText(timestamp) || !StringUtils.hasText(callback)
				|| !hasAttributionIdentity(feedback)) {
			return null;
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

	private String expandPromotionLink(String template, Map<String, String> macros, OsTypeEnum os) {
		String allowedTemplate = endpointPolicy.requireAllowedClickUrl(template);
		try {
			URI uri = URI.create(allowedTemplate);
			String rawQuery = uri.getRawQuery();
			if (!StringUtils.hasText(rawQuery)) {
				return null;
			}

			Map<String, String> expandedParameters = new LinkedHashMap<>();
			Set<String> parameterNames = new HashSet<>();
			boolean callbackMacroReplaced = false;
			for (String segment : rawQuery.split("&", -1)) {
				int separator = segment.indexOf('=');
				if (separator <= 0) {
					return null;
				}
				String name = URLDecoder.decode(segment.substring(0, separator), StandardCharsets.UTF_8).trim();
				String configuredValue = URLDecoder.decode(segment.substring(separator + 1), StandardCharsets.UTF_8);
				String normalizedName = name.toLowerCase(Locale.ROOT);
				if (!StringUtils.hasText(name) || !parameterNames.add(normalizedName)) {
					return null;
				}

				String expandedValue = configuredValue;
				if (macros.containsKey(configuredValue)) {
					expandedValue = macros.get(configuredValue);
					callbackMacroReplaced |= "callback".equals(normalizedName)
							&& "__CALLBACK__".equals(configuredValue) && StringUtils.hasText(expandedValue);
				}
				else if (containsUnresolvedMacro(configuredValue)) {
					return null;
				}
				else if (DEVICE_QUERY_PARAMETERS.contains(normalizedName)) {
					return null;
				}
				if (StringUtils.hasText(expandedValue)) {
					expandedParameters.put(normalizedName, expandedValue);
				}
			}

			if (!StringUtils.hasText(expandedParameters.get("aid"))
					|| !StringUtils.hasText(expandedParameters.get("did")) || !callbackMacroReplaced
					|| !StringUtils.hasText(expandedParameters.get("ts"))
					|| !StringUtils.hasText(expandedParameters.get("model"))
					|| !os.getIndex().equalsIgnoreCase(expandedParameters.get("os"))
					|| !hasExpandedAttributionIdentity(os, expandedParameters)) {
				return null;
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
			return requestUrl.toString();
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private boolean hasExpandedAttributionIdentity(OsTypeEnum os, Map<String, String> parameters) {
		boolean ipAndUa = StringUtils.hasText(parameters.get("ip")) && StringUtils.hasText(parameters.get("ua"));
		if (os == OsTypeEnum.IOS) {
			return StringUtils.hasText(parameters.get("caid1")) || StringUtils.hasText(parameters.get("caid2"))
					|| StringUtils.hasText(parameters.get("idfa")) || ipAndUa;
		}
		return StringUtils.hasText(parameters.get("oaid")) || StringUtils.hasText(parameters.get("imei"))
				|| ipAndUa;
	}

	private boolean containsUnresolvedMacro(String value) {
		return StringUtils.hasText(value) && value.matches(".*__[A-Za-z0-9_]+__.*");
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		if (feedback == null || !StringUtils.hasText(callbackBaseUrl)) {
			return null;
		}
		String callbackExt = AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey);
		if (!StringUtils.hasText(callbackExt)) {
			return null;
		}
		String state = feedback.getLId() + "_" + feedback.getTId();
		return callbackBaseUrl + "/weifeng/callback?ext=" + callbackExt + "&s=" + state
				+ "&event=__EVENT__&amount=__AMOUNT__&days=__DAYS__";
	}

	private boolean hasAttributionIdentity(FeedbackReq feedback) {
		boolean ipAndUa = StringUtils.hasText(feedback.getIp()) && StringUtils.hasText(feedback.getUa());
		if (feedback.getOs() == OsTypeEnum.IOS) {
			return StringUtils.hasText(firstText(feedback.getCaid1(), feedback.getCaid(), feedback.getCaid2(),
					feedback.getLastCaid(), feedback.getPrevCaid())) || StringUtils.hasText(feedback.getIdfa())
					|| ipAndUa;
		}
		return StringUtils.hasText(feedback.getOaid()) || StringUtils.hasText(feedback.getImei()) || ipAndUa;
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
