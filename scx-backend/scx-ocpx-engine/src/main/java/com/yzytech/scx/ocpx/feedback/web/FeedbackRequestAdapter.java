package com.yzytech.scx.ocpx.feedback.web;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;

/** 将媒体入站协议转换为 Engine 内部 Feedback 模型。 */
public interface FeedbackRequestAdapter {

	/**
	 * 标准化媒体 Feedback 请求。
	 * @param request HTTP Query 请求模型
	 * @return Engine 内部统一 Feedback
	 */
	FeedbackReq toFeedback(FeedbackIngressRequest request);

}
