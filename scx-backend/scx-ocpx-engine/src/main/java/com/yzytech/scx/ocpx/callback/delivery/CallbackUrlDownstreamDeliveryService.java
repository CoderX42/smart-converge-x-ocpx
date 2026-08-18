package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.TestCallbackCollectorService;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 使用 Feedback 中保存的通用回调地址向下游媒体回传转化事件。 */
@Service
@RequiredArgsConstructor
public class CallbackUrlDownstreamDeliveryService extends DownstreamDeliveryService {

	private final PublicCallbackUrlPolicy callbackUrlPolicy;

	private final TestCallbackCollectorService testCallbackCollectorService;

	/**
	 * 解码原媒体回调地址，追加 {@code event_type} 后发起 GET 请求。
	 * @param request 已通过 Callback 去重与扣量判定的请求
	 */
	@Override
	public void deliver(CallbackReq request) {
		Integer eventType = request.getActionType().getIndex();
		if (eventType == null) {
			log.info("downstream callback event is null");
			return;
		}
		if (request.getFeedback().getCallbackUrl() == null) {
			log.info("downstream callback URL is null");
			return;
		}
		// Feedback 入站时已严格 URL 编码，此处恢复媒体提供的原始地址。
		String callbackUrl = URLDecoder.decode(request.getFeedback().getCallbackUrl(), StandardCharsets.UTF_8);
		callbackUrl = callbackUrlPolicy.requirePublicHttpUrl(callbackUrl);
		callbackUrl = callbackUrlPolicy.appendCallbackFields(callbackUrl, eventType, request.getCurrencyAmount());
		callbackUrl = testCallbackCollectorService.signDeliveryIfTestCallback(callbackUrl, eventType,
				request.getCurrencyAmount());
		String content = NetUtil.httpGet(callbackUrl, null);
		if (StringUtils.hasText(content)) {
			log.debug("downstream callback response length:{}", content.length());
		}
	}

}
