package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.RtaDecisionPort;
import org.springframework.stereotype.Component;

@Component
public class DisabledRtaDecisionPort implements RtaDecisionPort {

	@Override
	public RtaDecision decide(RouteSnapshot route, FeedbackReqV1 feedback) {
		return RtaDecision.skipped();
	}

}
