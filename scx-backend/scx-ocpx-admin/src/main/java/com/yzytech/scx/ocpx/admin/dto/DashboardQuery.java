package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDate;

public record DashboardQuery(LocalDate startDate, LocalDate endDate, String advertiserId, String productId,
		String appLinkId, String taskId, String channelId, String mediaAccountId) {
}
