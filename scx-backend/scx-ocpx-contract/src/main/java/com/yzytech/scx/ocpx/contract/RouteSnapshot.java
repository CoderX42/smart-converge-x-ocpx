package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;

public record RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
		String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
		String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
		String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice, BigDecimal deductionRatio,
		String rtaMode, String billingMode, int clickDedupWindowSeconds) {

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode, 3600);
	}
}
