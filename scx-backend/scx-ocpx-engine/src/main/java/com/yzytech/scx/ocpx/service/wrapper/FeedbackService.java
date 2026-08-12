package com.yzytech.scx.ocpx.service.wrapper;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.service.result.FeedbackHandleResult;

public interface FeedbackService {

	boolean handleRta(FeedbackReq request);

	FeedbackHandleResult handleFeedback(FeedbackReq request);

}
