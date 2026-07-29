package com.yzytech.scx.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReportPullResult(String nextCursor, boolean hasMore, List<ReportRow> rows, LocalDateTime pulledAt) {
	public record ReportRow(String externalCampaignId, LocalDateTime dateHour, long impressions, long clicks,
			long conversions, BigDecimal spend, BigDecimal cpa) {
	}
}
