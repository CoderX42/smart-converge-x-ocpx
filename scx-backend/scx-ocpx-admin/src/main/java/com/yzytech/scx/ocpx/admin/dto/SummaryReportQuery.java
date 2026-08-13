package com.yzytech.scx.ocpx.admin.dto;

public record SummaryReportQuery(Integer dateType, String mediaId, String monitorTaskId, String advertiserId,
		String promotionLinkId, String startTime, String endTime, String asc, String desc, Long current, Long size) {
}
