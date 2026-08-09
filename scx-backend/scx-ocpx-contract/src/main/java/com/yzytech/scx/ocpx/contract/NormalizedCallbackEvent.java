package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedCallbackEvent(String eventId, String sourceEventId, String callbackRef, OcpxEventType eventType,
		OffsetDateTime actionTime, BigDecimal amount, String amountRaw, String sourceEventCode,
		String mappedUpstreamEventCode, String conversionEventCode, String mediaEventCode,
		BigDecimal assessmentUnitPrice, int dailyLimit) {

	public NormalizedCallbackEvent(String eventId, String sourceEventId, String callbackRef, OcpxEventType eventType,
			OffsetDateTime actionTime, BigDecimal amount, String amountRaw) {
		this(eventId, sourceEventId, callbackRef, eventType, actionTime, amount, amountRaw,
				eventType == null ? null : eventType.name(), eventType == null ? null : eventType.name(),
				eventType == null ? null : eventType.name(),
				eventType == null ? null : eventType.name(), BigDecimal.ZERO, 0);
	}

	public NormalizedCallbackEvent withSourceEventCode(String code) {
		return new NormalizedCallbackEvent(eventId, sourceEventId, callbackRef, eventType, actionTime, amount, amountRaw,
				code, mappedUpstreamEventCode, conversionEventCode, mediaEventCode, assessmentUnitPrice, dailyLimit);
	}

	public NormalizedCallbackEvent mapped(String sourceEventId, OcpxEventType conversionType,
			RouteEventSnapshot mapping) {
		return new NormalizedCallbackEvent(eventId, sourceEventId, callbackRef, conversionType, actionTime, amount,
				amountRaw, sourceEventCode, mapping.upstreamEventCode(), mapping.conversionEventCode(), mapping.mediaEventCode(),
				mapping.assessmentUnitPrice(), mapping.dailyLimit());
	}
}
