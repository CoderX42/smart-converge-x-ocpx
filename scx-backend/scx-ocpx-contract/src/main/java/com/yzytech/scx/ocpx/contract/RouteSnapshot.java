package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;

public record RouteSnapshot(String routeId, long routeVersion, String channelId, String channelCode,
		String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
		String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
		String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal deductionRatio, String rtaMode,
		String billingMode) {
}
