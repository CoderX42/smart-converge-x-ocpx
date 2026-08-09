package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.util.List;

public record RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
		String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
		String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
		String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice, BigDecimal deductionRatio,
		String rtaMode, String billingMode, int clickDedupWindowSeconds, List<RouteEventSnapshot> eventMappings,
		int eventSnapshotVersion) {

	public RouteSnapshot {
		eventMappings = eventMappings == null ? List.of() : List.copyOf(eventMappings);
		eventSnapshotVersion = Math.max(eventSnapshotVersion, 0);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode, int clickDedupWindowSeconds,
			List<RouteEventSnapshot> eventMappings) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode,
				clickDedupWindowSeconds, eventMappings, 1);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode, int clickDedupWindowSeconds) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode,
				clickDedupWindowSeconds, List.of(), 0);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode, 3600,
				List.of(), 0);
	}

	public RouteSnapshot withEventMappings(List<RouteEventSnapshot> mappings) {
		return new RouteSnapshot(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode,
				channelAuthType, channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode,
				upstreamSecret, bidUrl, upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode,
				billingMode, clickDedupWindowSeconds, mappings, eventSnapshotVersion);
	}
}
