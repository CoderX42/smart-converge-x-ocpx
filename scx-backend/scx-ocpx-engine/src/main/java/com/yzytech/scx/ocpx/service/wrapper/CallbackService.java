package com.yzytech.scx.ocpx.service.wrapper;

import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.service.result.CallbackHandleResult;

public interface CallbackService {

	CallbackHandleResult handleFeedback(CallbackReq request);

}
