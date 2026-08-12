package com.yzytech.scx.ocpx.contract;

/**
 * SCX 与一个下游媒体之间的双向协议适配器。
 *
 * <p>
 * 下游向 SCX 上报时由 V2 接入服务解析 FeedbackReqV2；SCX 把上游转化同步回传给下游时使用
 * CallbackReqV2。标准媒体通常直接选择 STANDARD。
 * </p>
 */
public interface DownstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	/** Build one downstream callback from the four-entity context and an in-memory credential. */
	AdapterHttpRequest buildCallback(DownstreamDeliveryContext context);

	/** Interpret both HTTP status and partner-specific business response semantics. */
	AdapterDeliveryResult evaluateCallbackResponse(AdapterHttpResponse response);

}
