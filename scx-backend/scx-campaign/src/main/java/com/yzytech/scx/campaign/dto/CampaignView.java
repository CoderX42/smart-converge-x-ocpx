package com.yzytech.scx.campaign.dto;

import java.math.BigDecimal;

public record CampaignView(String id, String adAccountId, String name, String billingMode, BigDecimal targetCpa,
		String status) {
}
