package com.yzytech.scx.ocpx.feedback.web;

import java.util.Map;

/** 媒体 Feedback 入口的兼容成功响应。 */
public record FeedbackIngressResponse(int ret, int code, String msg, Map<String, Object> data) {

	private static final FeedbackIngressResponse SUCCESS = new FeedbackIngressResponse(0, 200, "提交成功", Map.of());

	public static FeedbackIngressResponse success() {
		return SUCCESS;
	}

	/**
	 * 返回仍保持 HTTP 400 的可读错误信封。机器端可继续按状态码处理，浏览器直接打开时也能看到原因。
	 */
	public static FeedbackIngressResponse badRequest(String message) {
		return new FeedbackIngressResponse(1, 400, message, Map.of());
	}

}
