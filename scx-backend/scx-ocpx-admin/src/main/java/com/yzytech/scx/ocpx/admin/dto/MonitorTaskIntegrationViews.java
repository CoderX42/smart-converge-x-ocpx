package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MonitorTaskIntegrationViews {

	private MonitorTaskIntegrationViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedbackItem {

		private Long feedbackLogId;

		private LocalDateTime receivedAt;

		private String requestId;

		private String clickId;

		private String userId;

		private String userType;

		private String deviceOs;

		private String imei;

		private String oaid;

		private String androidId;

		private String mac;

		private String idfa;

		private String caid;

		private String caid1;

		private String ip;

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

		private boolean callbackAvailable;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page {

		private List<FeedbackItem> records;

		private long current;

		private long size;

		private long total;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CallbackResult {

		private String attemptId;

		private String sourceRequestId;

		private String eventCode;

		private Integer eventType;

		private BigDecimal currencyAmount;

		private String resultStatus;

		private Integer partnerHttpStatus;

		private String message;

		private long durationMs;

		private String responsePreview;

		private LocalDateTime sentAt;

	}

}
