package com.yzytech.scx.inventory.dto;

import java.util.Map;

public record PublishRequest(String campaignId, String externalCampaignId, String placementId,
		Map<String, Object> config, String idempotencyKey) {
}
