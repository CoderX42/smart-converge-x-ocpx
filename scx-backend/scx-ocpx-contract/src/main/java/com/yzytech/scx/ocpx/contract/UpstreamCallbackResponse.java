package com.yzytech.scx.ocpx.contract;

import java.util.Map;

/**
 * 上游适配器生成的 callback HTTP 响应。
 *
 * <p>
 * Body 使用 {@link Object}，允许不同媒体返回 JSON 对象、纯文本或其他可由 Spring HTTP 响应写出的格式；headers
 * 由适配器按媒体协议提供。通用 Controller 只负责把该对象转换成 HTTP 响应，不解释其中的业务字段。
 * </p>
 *
 * @param statusCode HTTP 状态码
 * @param headers 响应头，当前每个 header 使用单值
 * @param body 媒体协议定义的响应体
 */
public record UpstreamCallbackResponse(int statusCode, Map<String, String> headers, Object body) {

	public UpstreamCallbackResponse {
		if (statusCode < 100 || statusCode > 599) {
			throw new IllegalArgumentException("callback response statusCode is invalid");
		}
		headers = headers == null ? Map.of() : Map.copyOf(headers);
	}

}
