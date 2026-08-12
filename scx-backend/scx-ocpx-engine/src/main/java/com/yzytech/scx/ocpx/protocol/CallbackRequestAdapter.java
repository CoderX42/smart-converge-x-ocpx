package com.yzytech.scx.ocpx.protocol;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.web.UpstreamCallbackRequest;

/** 将上游回调协议转换为 Engine 内部 Callback 模型。 */
public interface CallbackRequestAdapter {

	/**
	 * 解析上游广告主 Callback 请求。
	 * @param request HTTP Query 请求模型
	 * @return Engine 内部统一 Callback
	 */
	CallbackReq toCallback(UpstreamCallbackRequest request);

}
