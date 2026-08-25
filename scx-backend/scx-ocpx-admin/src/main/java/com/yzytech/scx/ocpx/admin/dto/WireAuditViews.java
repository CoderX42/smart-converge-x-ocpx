package com.yzytech.scx.ocpx.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WireAuditViews {

	private WireAuditViews() {
	}

	@Data
	public static class Page {
		private List<Item> records;
		private long total;
		private long current;
		private long size;
	}

	@Data
	public static class Item {
		private String id;
		private String traceId;
		private String flowLogId;
		private String stageCode;
		private String direction;
		private String trafficClass;
		private String protocol;
		private String requestMethod;
		private String requestPath;
		private boolean wireSent;
		private String resultStatus;
		private Integer partnerHttpStatus;
		private String errorCode;
		private String requestId;
		private String advertiserId;
		private String promotionLinkId;
		private String mediaId;
		private String monitorTaskId;
		private long requestBytes;
		private long responseBytes;
		private String requestSha256;
		private String responseSha256;
		private String rawState;
		private String keyId;
		private int cryptoVersion;
		private String captureSource;
		private LocalDateTime capturedAt;
		private LocalDateTime finishedAt;
		private LocalDateTime rawExpiresAt;
		private LocalDateTime metadataExpiresAt;
	}

	@Data
	public static class Detail {
		private Item metadata;
		private Map<String, Object> request;
		private Map<String, Object> response;
	}

}
