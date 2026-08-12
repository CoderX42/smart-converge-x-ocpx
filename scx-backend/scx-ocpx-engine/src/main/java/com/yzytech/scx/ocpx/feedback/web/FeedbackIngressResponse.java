package com.yzytech.scx.ocpx.feedback.web;

import java.util.Map;

/** 媒体 Feedback 入口的兼容成功响应。 */
public record FeedbackIngressResponse(int ret, int code, String msg, Map<String, Object> data) {

	private static final FeedbackIngressResponse SUCCESS = new FeedbackIngressResponse(0, 200, "提交成功", Map.of());

	public static FeedbackIngressResponse success() {
		return SUCCESS;
	}

}
