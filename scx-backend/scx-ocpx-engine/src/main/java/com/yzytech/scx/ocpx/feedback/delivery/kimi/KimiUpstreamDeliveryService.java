package com.yzytech.scx.ocpx.feedback.delivery.kimi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.contract.AdvertiserMacroContract;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import com.yzytech.scx.ocpx.shared.util.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * KIMI 广告主点击投递实现（SGYZ KimiServiceImpl + KimiParamAdapter 出站半平移）。
 *
 * <p>
 * 仅点击事件投递；请求地址为广告主 {@code bidUrl} 宏替换结果，平台回调地址为 {@code {domain}/kimi/callback?ext={AES
 * 加密的 Feedback JSON}&s={lId}_{mId}}。
 */
@Component
public class KimiUpstreamDeliveryService extends UpstreamDeliveryService {

	@Value("${domain.name}")
	private String domainName;

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	public KimiUpstreamDeliveryService(OcpxAdvertiserIndex advertiserIndex, OcpxMonitorTaskIndex monitorTaskIndex) {
		this.advertiserIndex = advertiserIndex;
		this.monitorTaskIndex = monitorTaskIndex;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		try {
			if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
				return;
			}
			OcpxAdvertiser advertiser = advertiserIndex.get(feedback.getAId());
			String requestUrl = getReqUrl(advertiser, feedback);
			if (requestUrl == null) {
				return;
			}
			log.info("kimi linkId:{} req:{}", feedback.getLId(), requestUrl);
			String content = NetUtil.httpGet(requestUrl, null);
			log.info("kimi linkId:{}, content:{}", feedback.getLId(), content);
		}
		catch (Exception exception) {
			errorLog.error("执行 [kimi：{}] 异常 message:{}", feedback.getLId(), exception.getMessage(), exception);
			throw new RuntimeException(exception);
		}
	}

	/**
	 * 构造 KIMI 回调 SCX 的地址，并将完整 Feedback 加密进 {@code ext}。
	 * @param feedback 原始媒体 Feedback
	 * @return 可放入 KIMI 请求宏的 Callback URL
	 */
	public String getCallbackUrl(FeedbackReq feedback) {
		String state = feedback.getLId() + "_" + feedback.getMId();
		String callbackExt = AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey);
		return domainName + "/kimi/callback?ext=" + callbackExt + "&s=" + state;
	}

	/**
	 * 按 KIMI URL 模板生成最终 GET 地址。
	 *
	 * <p>
	 * 空字段保留原宏，UA、机型、Callback URL 和 {@code __BIZ_ID__} 使用严格 URL 编码；{@code __BIZ_ID__}
	 * 取监测任务 {@code bizId}（JSON 形态取 {@code trackingId} 键）。
	 * @param advertiser 上游广告主配置
	 * @param feedback 标准化媒体事件
	 * @return 已替换宏的请求地址
	 */
	public String getReqUrl(OcpxAdvertiser advertiser, FeedbackReq feedback) {
		if (advertiser == null) {
			return null;
		}
		String bidUrl = AdvertiserMacroContract.appendToBidUrl(advertiser.getBidUrl(), advertiser.getMacroParams());
		OcpxMonitorTask monitorTask = monitorTaskIndex.get(feedback.getTId());
		String bizId = "__BIZ_ID__";
		if (monitorTask != null) {
			bizId = monitorTask.getBizId();
			if (bizId != null && bizId.startsWith("{")) {
				Map<String, Object> map = JSON.parseObject(bizId);
				bizId = (String) map.get("trackingId");
			}
		}
		String callbackUrl = StringUtil.replaceDefaultEncode(getCallbackUrl(feedback), "__CALLBACK_URL__");
		String clickId = StringUtils.hasText(feedback.getClickId()) ? feedback.getClickId() : feedback.getReqId();
		String caidList = StringUtils.hasText(feedback.getCaidList()) ? feedback.getCaidList() : feedback.getCaid();
		return bidUrl.replace("__REQUEST_ID__", StringUtil.replaceDefault(feedback.getReqId(), "__REQUEST_ID__"))
			.replace("__CLICKID__", StringUtil.replaceDefault(clickId, "__CLICKID__"))
			.replace("__IMEI__", StringUtil.replaceDefault(feedback.getImei(), "__IMEI__"))
			.replace("__OAID__", StringUtil.replaceDefault(feedback.getOaid(), "__OAID__"))
			.replace("__OAID_MD5__", StringUtil.replaceDefault(feedback.getOaidMd5(), "__OAID_MD5__"))
			.replace("__IDFA__", StringUtil.replaceDefault(feedback.getIdfa(), "__IDFA__"))
			.replace("__IDFA_MD5__", StringUtil.replaceDefault(feedback.getIdfaMd5(), "__IDFA_MD5__"))
			.replace("__IMEI_MD5__", StringUtil.replaceDefault(feedback.getImeiMd5(), "__IMEI_MD5__"))
			.replace("__CAID_LIST__", StringUtil.replaceDefaultEncode(caidList, "__CAID_LIST__"))
			.replace("__AAID__", StringUtil.replaceDefault(feedback.getAaid(), "__AAID__"))
			.replace("__IP__", StringUtil.replaceDefault(feedback.getIp(), "__IP__"))
			.replace("__UA__", StringUtil.replaceDefaultEncode(feedback.getUa(), "__UA__"))
			.replace("__MODEL__", StringUtil.replaceDefaultEncode(feedback.getModel(), "__MODEL__"))
			.replace("__OS__", StringUtil.replaceDefault(matchOs(feedback.getOs()), "__OS__"))
			.replace("__MAC__", StringUtil.replaceDefault(feedback.getMac(), "__MAC__"))
			.replace("__CAID__", StringUtil.replaceDefault(feedback.getCaid(), "__CAID__"))
			.replace("__MTS__", StringUtil.replaceDefault(feedback.getTsMs(), "__MTS__"))
			.replace("__CALLBACK_URL__", callbackUrl)
			.replace("__CALLBACK__", callbackUrl)
			.replace("__CAMPAIGNID__", StringUtil.replaceDefault(feedback.getPlanId(), "__CAMPAIGNID__"))
			.replace("__ADGROUPID__", StringUtil.replaceDefault(feedback.getGroupId(), "__ADGROUPID__"))
			.replace("__CREATIVEID__", StringUtil.replaceDefault(feedback.getCreativeId(), "__CREATIVEID__"))
			.replace("__ADVERTISERID__", StringUtil.replaceDefault(feedback.getAdvId(), "__ADVERTISERID__"))
			.replace("__ORDER_PLAN_ID__", StringUtil.replaceDefault(feedback.getPlanId(), "__ORDER_PLAN_ID__"))
			.replace("__ORDER_GROUP_ID__", StringUtil.replaceDefault(feedback.getGroupId(), "__ORDER_GROUP_ID__"))
			.replace("__CREATIVE_ID__", StringUtil.replaceDefault(feedback.getCreativeId(), "__CREATIVE_ID__"))
			.replace("__ADVERTISER_ID__", StringUtil.replaceDefault(feedback.getAdvId(), "__ADVERTISER_ID__"))
			.replace("__BIZ_ID__", StringUtil.replaceDefaultEncode(bizId, "__BIZ_ID__"));
	}

	private String matchOs(OsTypeEnum os) {
		if (os == null) {
			return "android";
		}
		return switch (os) {
			case IOS -> "ios";
			default -> "android";
		};
	}

}
