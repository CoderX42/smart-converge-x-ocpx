package com.yzytech.scx.ocpx.protocol.template;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.web.UpstreamCallbackRequest;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.contract.AdvertiserMacroContract;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.CallbackRequestAdapter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import com.yzytech.scx.ocpx.shared.util.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 模板化 GET 协议的双向参数适配器。
 *
 * <p>
 * 正向链路替换广告主 URL 模板宏、生成加密 Callback 地址并追加签名；反向链路解密 {@code ext} 以恢复原始 Feedback 上下文。
 */
@Component
public class TemplateGetProtocolAdapter implements CallbackRequestAdapter {

	@Value("${domain.name}")
	private String domainName;

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	private final PartnerCredentialResolver credentialResolver;

	public TemplateGetProtocolAdapter(OcpxMonitorTaskIndex monitorTaskIndex,
			PartnerCredentialResolver credentialResolver) {
		this.monitorTaskIndex = monitorTaskIndex;
		this.credentialResolver = credentialResolver;
	}

	/**
	 * 构造广告主回调 SCX 的地址，并将完整 Feedback 加密进 {@code ext}。
	 * @param feedback 原始媒体 Feedback
	 * @return 可放入广告主请求宏的 Callback URL
	 */
	public String buildCallbackUrl(FeedbackReq feedback) {
		// s 保留旧协议状态格式；实际业务上下文以加密后的 ext 为准。
		String state = feedback.getLId() + "_" + feedback.getTId();
		String callbackExt = AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey);
		return domainName + "/callback?ext=" + callbackExt + "&s=" + state;
	}

	/**
	 * 解密广告主 Callback，恢复统一内部请求。
	 * @param request 广告主 Callback 查询参数
	 * @return 包含原 Feedback、动作类型和动作时间的请求
	 * @throws NullPointerException ext 无法解密时抛出，由 Controller 转换为 HTTP 500
	 */
	@Override
	public CallbackReq toCallback(UpstreamCallbackRequest request) {
		String ext = Objects.requireNonNull(AesUtil.decryptBase64(request.getExt(), callbackAesKey));
		FeedbackReq feedback = JSON.parseObject(ext, FeedbackReq.class);
		requireScxRouteIds(feedback);
		return CallbackReq.builder()
			.feedback(feedback)
			.actionType(ActionTypeEnum.getActionTypeEnum(request.getEvent_type()))
			.actionTime(request.getEvent_time())
			.build();
	}

	/**
	 * 按广告主 URL 模板生成最终 GET 地址。
	 *
	 * <p>
	 * 空字段保留原宏，UA、机型和 Callback URL 使用严格 URL 编码；签名为最终模板地址与广告主 {@code credentialConfigKey}
	 * 引用所解析的服务端密钥拼接后的 MD5。凭据不存在时拒绝生成请求地址。
	 * @param advertiser 上游广告主配置
	 * @param feedback 标准化媒体事件
	 * @return 已替换宏并追加 {@code signature} 的请求地址
	 */
	public String buildUpstreamRequestUrl(OcpxAdvertiser advertiser, FeedbackReq feedback) {
		String credential = credentialResolver.advertiserSecret(advertiser).orElse(null);
		if (!StringUtils.hasText(credential)) {
			return null;
		}
		String bidUrl = AdvertiserMacroContract.appendToBidUrl(advertiser.getBidUrl(), advertiser.getMacroParams());
		OcpxMonitorTask monitorTask = monitorTaskIndex.get(feedback.getTId());
		String bizId = monitorTask == null ? "__BIZ_ID__"
				: StringUtil.replaceDefault(monitorTask.getBizId(), "__BIZ_ID__");
		String callbackUrl = StringUtil.replaceDefaultEncode(buildCallbackUrl(feedback), "__CALLBACK_URL__");
		String clickId = StringUtils.hasText(feedback.getClickId()) ? feedback.getClickId() : feedback.getReqId();
		String caidList = StringUtils.hasText(feedback.getCaidList()) ? feedback.getCaidList() : feedback.getCaid();
		String requestUrl = bidUrl
			.replace("__REQUEST_ID__", StringUtil.replaceDefault(feedback.getReqId(), "__REQUEST_ID__"))
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
			.replace("__BIZ_ID__", bizId);
		String md5 = DigestUtils.md5DigestAsHex((requestUrl + credential).getBytes(StandardCharsets.UTF_8));
		return requestUrl + "&signature=" + md5;
	}

	private void requireScxRouteIds(FeedbackReq feedback) {
		if (feedback == null) {
			throw new IllegalArgumentException("callback ext is missing SCX route ids");
		}
		requireScxRouteId(feedback.getMId(), "mId");
		requireScxRouteId(feedback.getTId(), "tId");
		requireScxRouteId(feedback.getAId(), "aId");
		requireScxRouteId(feedback.getLId(), "lId");
	}

	private void requireScxRouteId(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException("callback ext is missing SCX route ids");
		}
		if (value.length() > 32) {
			throw new IllegalArgumentException("callback ext has invalid SCX route id: " + name);
		}
	}

	private String matchOs(OsTypeEnum os) {
		if (os == null) {
			return "3";
		}
		return switch (os) {
			case IOS -> "1";
			case ANDROID -> "0";
			default -> "3";
		};
	}

}
