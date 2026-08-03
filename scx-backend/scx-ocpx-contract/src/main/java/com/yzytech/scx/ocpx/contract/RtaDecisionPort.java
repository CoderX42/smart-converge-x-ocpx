package com.yzytech.scx.ocpx.contract;

public interface RtaDecisionPort {

	RtaDecision decide(RouteSnapshot route, FeedbackReqV1 feedback);

	record RtaDecision(String status, String reason) {
		public static RtaDecision skipped() {
			return new RtaDecision("SKIPPED", "RTA_OFF");
		}
	}

}
