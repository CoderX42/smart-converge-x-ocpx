package com.yzytech.scx.ocpx.protocol.template;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.callback.web.UpstreamCallbackRequest;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.contract.AdvertiserMacroContract;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.feedback.delivery.MacroResolutionService;
import com.yzytech.scx.ocpx.protocol.CallbackRequestAdapter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Value("${app.callback-crypto.keys.v1}")
	private String callbackAesKey;

	@Autowired(required = false)
	private OcpxKeyRingCrypto callbackCrypto;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	private final PartnerCredentialResolver credentialResolver;

	private final MacroResolutionService macroResolutionService;

	@Autowired
	public TemplateGetProtocolAdapter(OcpxMonitorTaskIndex monitorTaskIndex,
			PartnerCredentialResolver credentialResolver, MacroResolutionService macroResolutionService) {
		this.monitorTaskIndex = monitorTaskIndex;
		this.credentialResolver = credentialResolver;
		this.macroResolutionService = macroResolutionService;
	}

	/** 测试与旧嵌入调用兼容构造器。 */
	public TemplateGetProtocolAdapter(OcpxMonitorTaskIndex monitorTaskIndex,
			PartnerCredentialResolver credentialResolver) {
		this(monitorTaskIndex, credentialResolver, new MacroResolutionService());
	}

	/**
	 * 构造广告主回调 SCX 的地址，并将完整 Feedback 加密进 {@code ext}。
	 * @param feedback 原始媒体 Feedback
	 * @return 可放入广告主请求宏的 Callback URL
	 */
	public String buildCallbackUrl(FeedbackReq feedback) {
		// s 保留旧协议状态格式；实际业务上下文以加密后的 ext 为准。
		String state = feedback.getLId() + "_" + feedback.getTId();
		String callbackExt = callbackCrypto == null ? AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey)
				: callbackCrypto.encryptCallback(JSON.toJSONString(feedback));
		String kid = callbackCrypto == null ? "" : "&kid=" + callbackCrypto.activeKeyId();
		return domainName + "/callback?ext=" + callbackExt + kid + "&s=" + state;
	}

	/**
	 * 解密广告主 Callback，恢复统一内部请求。
	 * @param request 广告主 Callback 查询参数
	 * @return 包含原 Feedback、动作类型和动作时间的请求
	 * @throws NullPointerException ext 无法解密时抛出，由 Controller 转换为 HTTP 500
	 */
	@Override
	public CallbackReq toCallback(UpstreamCallbackRequest request) {
		String ext = Objects
			.requireNonNull(callbackCrypto == null ? AesUtil.decryptBase64(request.getExt(), callbackAesKey)
					: callbackCrypto.decryptCallback(request.getKid(), request.getExt()));
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
		return buildUpstreamRequest(advertiser, feedback).url();
	}

	public MacroResolutionService.Resolution buildUpstreamRequest(OcpxAdvertiser advertiser, FeedbackReq feedback) {
		if (advertiser == null || feedback == null) {
			return MacroResolutionService.Resolution.rejected("TEMPLATE_CONTEXT_MISSING");
		}
		String credential = credentialResolver.advertiserSecret(advertiser).orElse(null);
		if (!StringUtils.hasText(credential)) {
			return MacroResolutionService.Resolution.rejected("TEMPLATE_CREDENTIAL_MISSING");
		}
		String bidUrl = AdvertiserMacroContract.appendToBidUrl(advertiser.getBidUrl(), advertiser.getMacroParams());
		OcpxMonitorTask monitorTask = monitorTaskIndex.get(feedback.getTId());
		String bizId = monitorTask == null ? null : monitorTask.getBizId();
		MacroResolutionService.Resolution resolution = macroResolutionService.expandQueryTemplate(bidUrl, feedback,
				buildCallbackUrl(feedback), bizId);
		if (!resolution.success()) {
			return resolution;
		}
		String requestUrl = resolution.url();
		String md5 = DigestUtils.md5DigestAsHex((requestUrl + credential).getBytes(StandardCharsets.UTF_8));
		String signed = requestUrl + (requestUrl.contains("?") ? "&" : "?") + "signature=" + md5;
		return new MacroResolutionService.Resolution(signed, null, resolution.audit());
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

}
