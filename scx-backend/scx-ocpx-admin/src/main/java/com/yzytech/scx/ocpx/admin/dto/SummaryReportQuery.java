package com.yzytech.scx.ocpx.admin.dto;

public record SummaryReportQuery(Integer dateType, String mediaId, String taskId, String advertiserId,
		String promotionLinkId, String startTime, String endTime, String asc, String desc, Long current, Long size) {
}
