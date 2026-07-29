package com.yzytech.scx.inventory.dto;

import java.time.LocalDateTime;

public record PublishResult(boolean success, String externalCampaignId, String externalStatus, String errorCode,
		String errorMessage, LocalDateTime syncedAt) {
}
