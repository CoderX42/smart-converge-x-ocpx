package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.contract.AdvertiserMacroContract;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
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

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final AerimEndpointPolicy endpointPolicy;

	private final AerimHttpClient httpClient;

	private final OcpxFlowLogWriter flowLogWriter;

	public AerimUpstreamDeliveryService(OcpxAdvertiserIndex advertiserIndex, AerimEndpointPolicy endpointPolicy,
			AerimHttpClient httpClient, OcpxFlowLogWriter flowLogWriter) {
		this.advertiserIndex = advertiserIndex;
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
			String requestUrl = buildRequestUrl(advertiserIndex.get(feedback.getAId()), feedback);
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
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs());
		}
		catch (IllegalArgumentException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "AERIM_URL_REJECTED", 0L);
		}
		catch (RuntimeException exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "AERIM_INTERNAL_ERROR", 0L);
			throw exception;
		}
	}

	/** 构造不带签名、缺失宏参数已移除的 AERIM 正式请求地址。 */
	public String buildRequestUrl(OcpxAdvertiser advertiser, FeedbackReq feedback) {
		if (advertiser == null || !StringUtils.hasText(advertiser.getBidUrl())) {
			return null;
		}
		String template = AdvertiserMacroContract.appendToBidUrl(advertiser.getBidUrl(), advertiser.getMacroParams());
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
		return requestUrl;
	}

	public String buildCallbackUrl(FeedbackReq feedback) {
		String state = feedback.getLId() + "_" + feedback.getTId();
		String callbackExt = AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey);
		return callbackExt == null ? null
				: callbackBaseUrl + "/aerim/callback?ext=" + callbackExt + "&s=" + state;
	}

	private Map<String, String> macros(FeedbackReq feedback, String callback) {
		Map<String, String> values = new LinkedHashMap<>();
		put(values, "__REQUEST_ID__", feedback.getReqId());
		put(values, "__CLICKID__",
				StringUtils.hasText(feedback.getClickId()) ? feedback.getClickId() : feedback.getReqId());
		put(values, "__IMEI__", feedback.getImei());
		put(values, "__IMEI_MD5__", feedback.getImeiMd5());
		put(values, "__OAID__", feedback.getOaid());
		put(values, "__OAID_MD5__", feedback.getOaidMd5());
		put(values, "__IDFA__", feedback.getIdfa());
		put(values, "__IDFA_MD5__", feedback.getIdfaMd5());
		put(values, "__CAID__", feedback.getCaid());
		put(values, "__CAID_LIST__",
				StringUtils.hasText(feedback.getCaidList()) ? feedback.getCaidList() : feedback.getCaid());
		put(values, "__ANDROIDID__", feedback.getAndroidid());
		put(values, "__ANDROIDID_MD5__", feedback.getAndroididMd5());
		put(values, "__AAID__", feedback.getAaid());
		put(values, "__IP__", feedback.getIp());
		put(values, "__UA__", feedback.getUa());
		put(values, "__OS__", matchOs(feedback.getOs()));
		put(values, "__MODEL__", feedback.getModel());
		put(values, "__CALLBACK__", callback);
		put(values, "__CALLBACK_URL__", callback);
		put(values, "__CAMPAIGNID__", feedback.getPlanId());
		put(values, "__ADGROUPID__", feedback.getGroupId());
		put(values, "__CREATIVEID__", feedback.getCreativeId());
		put(values, "__ADVERTISERID__", feedback.getAdvId());
		return values;
	}

	private void put(Map<String, String> values, String macro, String value) {
		values.put(macro, StringUtils.hasText(value) && !RESIDUAL_MACRO.matcher(value).find()
				? NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8) : null);
	}

	private String replaceQueryPair(String pair, Map<String, String> macros) {
		String value = pair;
		for (Map.Entry<String, String> entry : macros.entrySet()) {
			if (!value.contains(entry.getKey())) {
				continue;
			}
			if (entry.getValue() == null) {
				return null;
			}
			value = value.replace(entry.getKey(), entry.getValue());
		}
		return RESIDUAL_MACRO.matcher(value).find() ? null : value;
	}

	private boolean hasCallbackParameter(String url) {
		int queryIndex = url.indexOf('?');
		if (queryIndex < 0) {
			return false;
		}
		for (String pair : url.substring(queryIndex + 1).split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0 && "callback".equals(pair.substring(0, separator)) && separator < pair.length() - 1) {
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
