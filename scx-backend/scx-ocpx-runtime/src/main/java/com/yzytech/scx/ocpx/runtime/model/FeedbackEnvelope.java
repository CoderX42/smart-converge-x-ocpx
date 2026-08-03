package com.yzytech.scx.ocpx.runtime.model;

import com.yzytech.scx.ocpx.contract.OcpxEventType;

import java.time.OffsetDateTime;

public record FeedbackEnvelope(String eventId, String payloadHash, OcpxEventType eventType, String channelCode,
		String routeId, long routeVersion, OffsetDateTime occurredAt, OffsetDateTime receivedAt, String deviceKeyHash,
		String callbackRef, String encryptedFeedback, String encryptedRoute, boolean forwardUpstream) {
}
