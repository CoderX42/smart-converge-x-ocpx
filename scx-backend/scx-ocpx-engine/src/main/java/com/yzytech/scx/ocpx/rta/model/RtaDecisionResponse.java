package com.yzytech.scx.ocpx.rta.model;

import com.yzytech.scx.ocpx.rta.enums.RtaDecision;

import java.util.List;



/** RTA 入口的稳定响应结构。 */
public record RtaDecisionResponse(String requestId, RtaDecision decision, List<String> taskIds,
		String reasonCode) {
}
