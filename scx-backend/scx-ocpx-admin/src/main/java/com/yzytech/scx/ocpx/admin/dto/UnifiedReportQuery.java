package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDate;

public record UnifiedReportQuery(LocalDate startDate, LocalDate endDate, String groupBy, String advertiserId,
		String promotionLinkId, String mediaId, String monitorTaskId, long current, long size) {
}
