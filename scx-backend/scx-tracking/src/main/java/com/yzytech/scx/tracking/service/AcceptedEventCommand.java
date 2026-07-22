package com.yzytech.scx.tracking.service;

import java.time.OffsetDateTime;

public record AcceptedEventCommand(String receiptId, String clientId, String sourceCode, String eventId,
		String taskCode, String eventType, OffsetDateTime occurredAt, OffsetDateTime receivedAt, String devicePlatform,
		String deviceIdType, String deviceIdVersion, String deviceKeyVersion, String deviceKeyHash,
		String businessEventKeyHash, String payloadHash, String payloadJson, String callbackRef,
		String callbackUrlCiphertext, String callbackUrlIv, String callbackUrlHost, String callbackUrlFingerprint) {
}
