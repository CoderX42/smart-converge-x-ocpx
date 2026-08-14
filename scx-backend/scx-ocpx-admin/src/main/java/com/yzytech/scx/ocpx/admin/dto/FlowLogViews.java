package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class FlowLogViews {

	private FlowLogViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String flowType;

		private String eventId;

		private String sourceEventId;

		private String eventCode;

		private String advertiserId;

		private String advertiserName;

		private String promotionLinkId;

		private String promotionLinkName;

		private String mediaId;

		private String mediaName;

		private String monitorTaskId;

		private String monitorTaskName;

		private String resultStatus;

		private Integer partnerHttpStatus;

		private String message;

		private long durationMs;

		private LocalDateTime eventTime;

		private LocalDateTime createTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page {

		private List<Item> records;

		private long current;

		private long size;

		private long total;

	}

}
