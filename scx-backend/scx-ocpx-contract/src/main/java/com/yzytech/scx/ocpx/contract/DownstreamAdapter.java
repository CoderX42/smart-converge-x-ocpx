package com.yzytech.scx.ocpx.contract;

import reactor.core.publisher.Mono;

/**
 * SCX 与一个下游渠道之间的双向协议适配器。
 *
 * <p>
 * 下游向 SCX 上报时使用 {@link #parseFeedback}；SCX 把上游转化回传给下游时使用
 * {@link #buildCallback}。标准渠道通常直接选择 STANDARD，不需要为每个渠道新增 Java 类。
 * </p>
 */
public interface DownstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	Mono<FeedbackReqV1> parseFeedback(String rawBody);

	Mono<AdapterHttpRequest> buildCallback(RouteSnapshot route, CallbackReqV1 callback);

	Mono<AdapterDeliveryResult> evaluateCallbackResponse(AdapterHttpResponse response);

}
