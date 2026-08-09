package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;

/** Immutable event mapping captured with a published route version. */
public record RouteEventSnapshot(String eventMappingId, String upstreamEventCode, String upstreamEventName,
		String conversionEventCode, String conversionEventName, String mediaEventCode, String mediaEventName,
		BigDecimal assessmentUnitPrice, int dailyLimit) {
}
