package com.yzytech.scx.adx.dto;

import java.time.LocalDate;
import java.util.List;

public record UnifiedReportQuery(LocalDate startDate, LocalDate endDate, String grain, String groupBy,
		String mediaLevel, String advertiserId, String productId, String appLinkId, String taskId, String channelId,
		String mediaAccountId, List<String> metrics, String sort, String direction, long current, long size) {
}
