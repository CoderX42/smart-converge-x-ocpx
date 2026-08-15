package com.yzytech.scx.ocpx.feedback.service;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;

/** Feedback 事件处理入口。 */
public interface FeedbackService {

	/**
	 * 对媒体事件执行 MySQL 去重、CPC 计费、小时汇总和受控广告主转发。
	 * @param request 标准化媒体事件
	 * @return 是否首次收到该请求与事件组合
	 */
	FeedbackHandleResult handleFeedback(FeedbackReq request);

}
