package com.yzytech.scx.tracking.dto;

public record EventIngestionResponse(String rawEventId, boolean duplicateDelivery, String processingStatus,
		String reasonCode, String attributionStatus) {
}
