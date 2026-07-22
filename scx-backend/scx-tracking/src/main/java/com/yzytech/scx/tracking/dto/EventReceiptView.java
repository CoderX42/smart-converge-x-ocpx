package com.yzytech.scx.tracking.dto;

import java.time.OffsetDateTime;

public record EventReceiptView(String receiptId, String eventId, boolean duplicateDelivery, String acceptanceStatus,
		OffsetDateTime receivedAt, String processingStatus, String reasonCode, String attributionStatus) {
}
