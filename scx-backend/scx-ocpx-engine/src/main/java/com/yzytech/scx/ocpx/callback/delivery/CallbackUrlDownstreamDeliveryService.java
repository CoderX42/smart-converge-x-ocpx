package com.yzytech.scx.ocpx.callback.delivery;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 使用 Feedback 中保存的通用回调地址向下游媒体回传转化事件。 */
@Service
public class CallbackUrlDownstreamDeliveryService extends DownstreamDeliveryService {

	/**
	 * 解码原媒体回调地址，追加 {@code event_type} 后发起 GET 请求。
	 * @param request 已通过 Callback 去重与扣量判定的请求
	 */
	@Override
	public void deliver(CallbackReq request) {
		Integer eventType = request.getActionType().getIndex();
		if (eventType == null) {
			log.info("downstream callback event is null:{}", JSON.toJSONString(request));
			return;
		}
		if (request.getFeedback().getCallbackUrl() == null) {
			log.info("downstream callback URL is null");
			return;
		}
		// Feedback 入站时已严格 URL 编码，此处恢复媒体提供的原始地址。
		String callbackUrl = URLDecoder.decode(request.getFeedback().getCallbackUrl(), StandardCharsets.UTF_8);
		callbackUrl = callbackUrl + "&event_type=" + eventType;
		String content = NetUtil.httpGet(callbackUrl, null);
		if (StringUtils.hasText(content)) {
			log.info("downstream callback response:{}", content);
		}
	}

}
