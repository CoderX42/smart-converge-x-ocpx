package com.yzytech.scx.inventory.dto;

import java.time.LocalDateTime;

public record StatusQueryResult(String externalCampaignId, String externalStatus, LocalDateTime lastUpdatedAt,
		boolean available) {
}
