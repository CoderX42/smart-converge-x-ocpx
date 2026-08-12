package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedCallbackEvent(String eventId, String sourceEventId, String callbackRef, OcpxEventType eventType,
		OffsetDateTime actionTime, BigDecimal amount, String amountRaw, String sourceEventCode) {

	public NormalizedCallbackEvent(String eventId, String sourceEventId, String callbackRef, OcpxEventType eventType,
			OffsetDateTime actionTime, BigDecimal amount, String amountRaw) {
		this(eventId, sourceEventId, callbackRef, eventType, actionTime, amount, amountRaw,
				eventType == null ? null : eventType.name());
	}

	public NormalizedCallbackEvent withSourceEventCode(String code) {
		return new NormalizedCallbackEvent(eventId, sourceEventId, callbackRef, eventType, actionTime, amount, amountRaw,
				code);
	}
}
