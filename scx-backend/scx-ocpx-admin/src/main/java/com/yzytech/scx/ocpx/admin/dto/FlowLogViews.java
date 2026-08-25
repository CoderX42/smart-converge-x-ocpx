package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowLogViews {

	private FlowLogViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String flowType;

		private String traceId;

		private String stageCode;

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

		private boolean hasNext;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String label;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Options {

		private List<Option> advertisers;

		private List<Option> promotionLinks;

		private List<Option> media;

		private List<Option> monitorTasks;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RequestDetail {

		private String flowLogId;

		private String flowType;

		private String traceId;

		private String coverageStatus;

		private List<Stage> stages;

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

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Stage {

		private String id;

		private String stageCode;

		private String flowType;

		private String eventCode;

		private String resultStatus;

		private String requestMethod;

		private String requestEndpoint;

		private Integer partnerHttpStatus;

		private long durationMs;

		private String message;

		private Map<String, Object> requestParams;

		private Map<String, Object> macroAudit;

		private String responsePreview;

		private boolean requestTruncated;

		private LocalDateTime eventTime;

	}

}
