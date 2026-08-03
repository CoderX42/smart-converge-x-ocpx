package com.yzytech.scx.ocpx.runtime.model;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;

public record DeliveryContext(DeliveryJob job, RouteSnapshot route, FeedbackReqV1 feedback,
		NormalizedCallbackEvent callback) {
}
