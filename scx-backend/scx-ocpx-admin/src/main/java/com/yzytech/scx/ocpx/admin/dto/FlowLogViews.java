package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class FlowLogViews {

	private FlowLogViews() {
	}

	public record Item(String id, String flowType, String eventId, String sourceEventId, String eventCode,
			String advertiserId, String advertiserName, String promotionLinkId, String promotionLinkName,
			String mediaId, String mediaName, String monitorTaskId, String monitorTaskName, String resultStatus,
			Integer partnerHttpStatus, String message, long durationMs, LocalDateTime eventTime,
			LocalDateTime createTime) {
	}

	public record Page(List<Item> records, long current, long size, long total) {
	}

}
