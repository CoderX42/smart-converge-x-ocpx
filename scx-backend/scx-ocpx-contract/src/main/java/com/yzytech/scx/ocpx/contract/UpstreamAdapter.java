package com.yzytech.scx.ocpx.contract;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * SCX 与一个上游广告主之间的双向协议适配器。
 *
 * <p>
 * 下游事件发往上游时，同步 Feedback 服务从四张业务表组装上下文并由 Adapter 构造请求；
 * 上游把转化回调给 SCX 时使用 {@link #parseCallback}。Adapter 不负责数据库查询或日志。
 * </p>
 *
 * <p>
 * 协议处理方法返回 {@link Mono}，因此解析或校验异常必须作为 error 信号返回。Adapter 不允许调用 {@code subscribe()} 或
	 * {@code block()}，订阅由 WebFlux 入口统一负责。
 * </p>
 */
public interface UpstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	boolean supports(OcpxEventType eventType);

	/** Build one upstream request from the four-entity context and an in-memory credential. */
	AdapterHttpRequest buildFeedback(UpstreamDeliveryContext context);

	/** Interpret both HTTP status and partner-specific business response semantics. */
	AdapterDeliveryResult evaluateFeedbackResponse(AdapterHttpResponse response);

	/**
	 * 从当前上游的 callback 参数中提取 SCX 回调关联 token。参数名称属于广告主协议的一部分，不能由通用入口固定。
	 */
	Mono<String> callbackToken(Map<String, String> parameters);

	/** 将当前上游的私有 callback 参数归一化为 SCX 内部事件。 */
	Mono<NormalizedCallbackEvent> parseCallback(String callbackRef, Map<String, String> parameters);

	/**
	 * 构造当前上游协议要求的成功响应。
	 * @param parameters 上游传入的原始 query 参数，可用于协议要求的字段回显
	 * @param eventId SCX 归一化事件 ID
	 * @param status 幂等处理状态，例如 ACCEPTED 或 DUPLICATE
	 */
	Mono<UpstreamCallbackResponse> callbackSuccess(Map<String, String> parameters, String eventId, String status);

	/**
	 * 构造当前上游协议要求的失败响应。
	 * @param parameters 上游传入的原始 query 参数，可用于协议要求的字段回显
	 * @param httpStatus SCX 对该错误建议使用的 HTTP 状态
	 * @param message 已归一化的安全错误说明
	 */
	Mono<UpstreamCallbackResponse> callbackFailure(Map<String, String> parameters, int httpStatus, String message);

}
