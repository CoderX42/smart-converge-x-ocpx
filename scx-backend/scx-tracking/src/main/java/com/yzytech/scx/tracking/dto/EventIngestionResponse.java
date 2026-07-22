package com.yzytech.scx.tracking.dto;

import java.time.OffsetDateTime;

public record EventIngestionResponse(String receiptId, String eventId, boolean duplicateDelivery,
		String acceptanceStatus, OffsetDateTime receivedAt) {
}
