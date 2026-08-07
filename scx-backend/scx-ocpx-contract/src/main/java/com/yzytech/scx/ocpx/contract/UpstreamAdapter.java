package com.yzytech.scx.ocpx.contract;

import java.util.Map;

public interface UpstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	boolean supports(OcpxEventType eventType);

	/**
	 * Validate adapter-specific protocol requirements before a reliable delivery job is
	 * created. Implementations must not perform network I/O here.
	 */
	default void validateFeedback(RouteSnapshot route, FeedbackReqV1 feedback) {
	}

	AdapterHttpRequest buildFeedback(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl);

	boolean isSuccess(AdapterHttpResponse response);

	default String failureReason(AdapterHttpResponse response) {
		return "UPSTREAM_REJECTED";
	}

	/**
	 * 从当前上游的 callback 参数中提取 SCX 回调关联 token。参数名称属于媒体协议的一部分，不能由通用入口固定。
	 */
	String callbackToken(Map<String, String> parameters);

	/** 将当前上游的私有 callback 参数归一化为 SCX 内部事件。 */
	NormalizedCallbackEvent parseCallback(String callbackRef, Map<String, String> parameters);

	/**
	 * 构造当前上游协议要求的成功响应。
	 * @param parameters 上游传入的原始 query 参数，可用于协议要求的字段回显
	 * @param eventId SCX 归一化事件 ID
	 * @param status 幂等处理状态，例如 ACCEPTED 或 DUPLICATE
	 */
	UpstreamCallbackResponse callbackSuccess(Map<String, String> parameters, String eventId, String status);

	/**
	 * 构造当前上游协议要求的失败响应。
	 * @param parameters 上游传入的原始 query 参数，可用于协议要求的字段回显
	 * @param httpStatus SCX 对该错误建议使用的 HTTP 状态
	 * @param message 已归一化的安全错误说明
	 */
	UpstreamCallbackResponse callbackFailure(Map<String, String> parameters, int httpStatus, String message);

}
