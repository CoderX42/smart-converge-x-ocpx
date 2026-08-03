package com.yzytech.scx.ocpx.contract;

import java.time.OffsetDateTime;

public record NormalizedFeedbackEvent(String eventId, String payloadHash, OcpxEventType eventType, String channelCode,
		String routeId, long routeVersion, OffsetDateTime occurredAt, OffsetDateTime receivedAt, String deviceKeyHash) {
}
