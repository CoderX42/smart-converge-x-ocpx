package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedCallbackEvent(String eventId, String sourceEventId, String callbackRef, OcpxEventType eventType,
		OffsetDateTime actionTime, BigDecimal amount, String amountRaw) {
}
