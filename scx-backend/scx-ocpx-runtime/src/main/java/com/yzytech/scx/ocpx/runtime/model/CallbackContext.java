package com.yzytech.scx.ocpx.runtime.model;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;

public record CallbackContext(String callbackRef, String feedbackEventId, RouteSnapshot route, FeedbackReqV1 feedback,
		String debugExecutionId) {

	public CallbackContext(String callbackRef, String feedbackEventId, RouteSnapshot route, FeedbackReqV1 feedback) {
		this(callbackRef, feedbackEventId, route, feedback, null);
	}
}
