package com.yzytech.scx.ocpx.contract;

import java.util.Map;

/**
 * Optional, server-side-only debug description for an adapter.
 *
 * <p>
 * The returned values are valid protocol examples, not credentials. Adapters that do not
 * implement this contract remain available for preview and custom HTTP requests, but
 * cannot be used to synthesize a real integrated execution.
 * </p>
 */
public interface AdapterDebugSupport {

	FeedbackReqV1 sampleFeedback(RouteSnapshot route, String executionId);

	Map<String, String> sampleCallback(RouteSnapshot route, String callbackToken, String executionId);

}
