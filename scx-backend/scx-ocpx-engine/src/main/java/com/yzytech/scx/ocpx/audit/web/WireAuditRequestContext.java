package com.yzytech.scx.ocpx.audit.web;

import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import jakarta.servlet.http.HttpServletRequest;

public final class WireAuditRequestContext {

	public static final String ROUTE_ATTRIBUTE = WireAuditRequestContext.class.getName() + ".route";

	private WireAuditRequestContext() {
	}

	public static void enrich(HttpServletRequest request, FeedbackReq feedback) {
		if (request != null)
			request.setAttribute(ROUTE_ATTRIBUTE, WireAuditService.Route.from(feedback));
	}

	public static void enrich(HttpServletRequest request, CallbackReq callback) {
		if (request != null)
			request.setAttribute(ROUTE_ATTRIBUTE, WireAuditService.Route.from(callback));
	}

}
