package com.yzytech.scx.ocpx.rta.model;

import com.yzytech.scx.ocpx.rta.enums.RtaDecision;

import java.util.List;



/** RTA 适配器的脱敏业务结果。 */
public record RtaAdapterResult(RtaDecision decision, String outcomeClass, String reasonCode,
		List<String> taskIds) {
}
