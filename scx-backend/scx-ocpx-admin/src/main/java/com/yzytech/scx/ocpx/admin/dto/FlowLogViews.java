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

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RequestDetail {

		private String flowLogId;

		private String flowType;

		private String eventId;

		private String sourceEventId;

		private String eventCode;

		private String resultStatus;

		private Integer partnerHttpStatus;

		private String message;

		private String responsePreview;

		private long durationMs;

		private LocalDateTime eventTime;

		private LocalDateTime flowCreatedAt;

		private Long feedbackLogId;

		private LocalDateTime receivedAt;

		private String requestId;

		private String clickId;

		private String impressionId;

		private String userId;

		private String userType;

		private String deviceOs;

		private String osVersion;

		private String model;

		private String imei;

		private String imeiMd5;

		private String oaid;

		private String oaidMd5;

		private String androidId;

		private String androidIdMd5;

		private String idfa;

		private String idfaMd5;

		private String aaid;

		private String caid;

		private String caid1;

		private String caid2;

		private String caidList;

		private String lastCaid;

		private String previousCaid;

		private String mac;

		private String ip;

		private String ipv6;

		private String ua;

		private String callbackUrl;

		private String timestamp;

		private String timestampMs;

		private String externalAdvertiserId;

		private String groupId;

		private String planId;

		private String creativeId;

		private String mediaId;

		private String advertiserId;

		private String monitorTaskId;

		private String promotionLinkId;

	}

}
