package com.yzytech.scx.ocpx.contract;

public interface DownstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	FeedbackReqV1 parseFeedback(String rawBody);

	AdapterHttpRequest buildCallback(RouteSnapshot route, CallbackReqV1 callback);

	boolean isSuccess(AdapterHttpResponse response);

}
