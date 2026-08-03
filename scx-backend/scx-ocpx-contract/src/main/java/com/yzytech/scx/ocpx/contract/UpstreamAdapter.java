package com.yzytech.scx.ocpx.contract;

import java.util.Map;

public interface UpstreamAdapter {

	String code();

	AdapterCapabilities capabilities();

	boolean supports(OcpxEventType eventType);

	AdapterHttpRequest buildFeedback(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl);

	boolean isSuccess(AdapterHttpResponse response);

	NormalizedCallbackEvent parseCallback(String callbackRef, Map<String, String> parameters);

}
