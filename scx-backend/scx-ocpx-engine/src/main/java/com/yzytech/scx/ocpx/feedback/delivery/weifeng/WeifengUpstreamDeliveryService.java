package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
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
import java.util.LinkedHashMap;
import java.util.Map;

/** 微风互动 v1.3 点击监测 GET 投递实现。 */
@Component
public class WeifengUpstreamDeliveryService extends UpstreamDeliveryService {

	@Value("${ocpx.weifeng.callback-base-url:${domain.name}}")
	private String callbackBaseUrl;

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	private final WeifengProperties properties;

	private final WeifengEndpointPolicy endpointPolicy;

	private final WeifengHttpClient httpClient;

	private final OcpxFlowLogWriter flowLogWriter;

	public WeifengUpstreamDeliveryService(OcpxAdvertiserIndex advertiserIndex, OcpxMonitorTaskIndex monitorTaskIndex,
			WeifengProperties properties, WeifengEndpointPolicy endpointPolicy, WeifengHttpClient httpClient,
			OcpxFlowLogWriter flowLogWriter) {
		this.advertiserIndex = advertiserIndex;
		this.monitorTaskIndex = monitorTaskIndex;
		this.properties = properties;
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

	/** 构造字段完整、每个值仅编码一次的微风点击地址。 */
	public String buildRequestUrl(FeedbackReq feedback) {
		if (feedback == null || feedback.getOs() == null || feedback.getOs() == OsTypeEnum.UNKNOWN) {
			return null;
		}
		OcpxAdvertiser advertiser = advertiserIndex.get(feedback.getAId());
		OcpxMonitorTask monitorTask = monitorTaskIndex.get(feedback.getTId());
		String did = properties.getMediaIds().get(feedback.getMId());
		String aid = monitorTask == null ? null : monitorTask.getBizId();
		String timestamp = timestampMillis(feedback);
		String callback = buildCallbackUrl(feedback);
		if (advertiser == null || !StringUtils.hasText(advertiser.getBidUrl()) || !StringUtils.hasText(aid)
				|| !StringUtils.hasText(did) || !StringUtils.hasText(feedback.getModel())
				|| !StringUtils.hasText(timestamp) || !StringUtils.hasText(callback)
				|| !hasAttributionIdentity(feedback)) {
			return null;
		}

		Map<String, String> parameters = new LinkedHashMap<>();
		parameters.put("caid1", firstText(feedback.getCaid1(), feedback.getCaid()));
		parameters.put("caid2", firstText(feedback.getCaid2(), feedback.getLastCaid(), feedback.getPrevCaid()));
		parameters.put("idfa", feedback.getIdfa());
		parameters.put("oaid", feedback.getOaid());
		parameters.put("imei", feedback.getImei());
		parameters.put("androidid", feedback.getAndroidid());
		parameters.put("ip", feedback.getIp());
		parameters.put("ipv6", feedback.getIpv6());
		parameters.put("ua", feedback.getUa());
		parameters.put("ts", timestamp);
		parameters.put("os", feedback.getOs().getIndex());
		parameters.put("osv", feedback.getOsv());
		parameters.put("model", feedback.getModel());
		parameters.put("aid", aid);
		parameters.put("did", did);
		parameters.put("callback", callback);

		StringBuilder requestUrl = new StringBuilder(advertiser.getBidUrl());
		String separator = advertiser.getBidUrl().contains("?") ? "&" : "?";
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			if (!StringUtils.hasText(entry.getValue())) {
				continue;
			}
			requestUrl.append(separator)
				.append(entry.getKey())
				.append('=')
				.append(NetUtil.strictUrlEncode(entry.getValue(), StandardCharsets.UTF_8));
			separator = "&";
		}
		return requestUrl.toString();
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
