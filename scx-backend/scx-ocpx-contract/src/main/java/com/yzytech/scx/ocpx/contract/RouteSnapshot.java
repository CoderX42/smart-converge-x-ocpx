package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.util.List;

public record RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
		String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
		String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
		String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice, BigDecimal deductionRatio,
		String rtaMode, String billingMode, int clickDedupWindowSeconds, List<RouteEventSnapshot> eventMappings,
		int eventSnapshotVersion, String eventMappingMode, String tenantId, String productId, String taskId,
		String mediaAccountId, String operatorUserId, String routeVersionId) {

	public RouteSnapshot {
		if (!"OFF".equals(rtaMode)) {
			throw new IllegalArgumentException("ADX_RTA_BLOCKED_BY_ADR: route snapshot RTA mode must be OFF");
		}
		eventMappings = eventMappings == null ? List.of() : List.copyOf(eventMappings);
		eventSnapshotVersion = Math.max(eventSnapshotVersion, 0);
		if (eventMappingMode == null || eventMappingMode.isBlank()) {
			eventMappingMode = eventSnapshotVersion == 0 ? "LEGACY_UNMAPPED"
					: eventSnapshotVersion == 1 ? "LEGACY_V80_SNAPSHOT" : "SNAPSHOT";
		}
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode, int clickDedupWindowSeconds,
			List<RouteEventSnapshot> eventMappings, int eventSnapshotVersion, String eventMappingMode) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode,
				clickDedupWindowSeconds, eventMappings, eventSnapshotVersion, eventMappingMode, null, null, null, null,
				null, null);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode, int clickDedupWindowSeconds,
			List<RouteEventSnapshot> eventMappings, int eventSnapshotVersion) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode,
				clickDedupWindowSeconds, eventMappings, eventSnapshotVersion, null, null, null, null, null, null, null);
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
				clickDedupWindowSeconds, eventMappings, 1, "LEGACY_V80_SNAPSHOT", null, null, null, null, null, null);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode, int clickDedupWindowSeconds) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode,
				clickDedupWindowSeconds, List.of(), 0, "LEGACY_UNMAPPED", null, null, null, null, null, null);
	}

	public RouteSnapshot(String routeId, String bizId, long routeVersion, String channelId, String channelCode,
			String channelAdapterCode, String channelAuthType, int channelKeyVersion, String channelSecret,
			String advertiserId, String appLinkId, String upstreamAdapterCode, String upstreamSecret, String bidUrl,
			String upstreamRequiredMacros, String downstreamCallbackUrl, BigDecimal unitPrice,
			BigDecimal deductionRatio, String rtaMode, String billingMode) {
		this(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode, channelAuthType,
				channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode, upstreamSecret, bidUrl,
				upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio, rtaMode, billingMode, 3600,
				List.of(), 0, "LEGACY_UNMAPPED");
	}

	public RouteSnapshot withEventMappings(List<RouteEventSnapshot> mappings) {
		return new RouteSnapshot(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode,
				channelAuthType, channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode,
				upstreamSecret, bidUrl, upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio,
				rtaMode, billingMode, clickDedupWindowSeconds, mappings, eventSnapshotVersion, eventMappingMode,
				tenantId, productId, taskId, mediaAccountId, operatorUserId, routeVersionId);
	}

	public RouteSnapshot withLegacyEventAdapter() {
		if (eventSnapshotVersion != 0) {
			throw new IllegalStateException(
					"explicit legacy adapter is only valid for routes without an event snapshot");
		}
		return new RouteSnapshot(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode,
				channelAuthType, channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode,
				upstreamSecret, bidUrl, upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio,
				rtaMode, billingMode, clickDedupWindowSeconds, eventMappings, 0, "LEGACY_ADAPTER", tenantId, productId,
				taskId, mediaAccountId, operatorUserId, routeVersionId);
	}

	public RouteSnapshot withFactDimensions(String tenant, String product, String task, String mediaAccount,
			String operator, String immutableRouteVersionId) {
		return new RouteSnapshot(routeId, bizId, routeVersion, channelId, channelCode, channelAdapterCode,
				channelAuthType, channelKeyVersion, channelSecret, advertiserId, appLinkId, upstreamAdapterCode,
				upstreamSecret, bidUrl, upstreamRequiredMacros, downstreamCallbackUrl, unitPrice, deductionRatio,
				rtaMode, billingMode, clickDedupWindowSeconds, eventMappings, eventSnapshotVersion, eventMappingMode,
				tenant, product, task, mediaAccount, operator, immutableRouteVersionId);
	}
}
