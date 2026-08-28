package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MonitorTaskTestViews {

	private MonitorTaskTestViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Parameter {

		private String key;

		private String label;

		private String group;

		private String macro;

		private boolean configuredByMedia;

		private boolean forwardedUpstream;

		private boolean selected;

		private boolean required;

		private boolean editable;

		private String value;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Preview {

		private String requestId;

		private String event;

		private String os;

		private String taskId;

		private String monitorName;

		private String advertiserName;

		private String mediaName;

		private String promotionLinkName;

		private Integer platform;

		private String testUrl;

		private String callbackToken;

		private String callbackMode;

		private String callbackDisplayUrl;

		private BigDecimal expectedCharge;

		private BigDecimal availableBalance;

		private boolean executable;

		private List<String> blockers;

		private List<String> warnings;

		private List<Parameter> parameters;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Execution {

		private String requestId;

		private String testUrl;

		private boolean alreadyExecuted;

		private Integer engineHttpStatus;

		private String engineResponse;

		private String errorCode;

		private long durationMs;

		private LocalDateTime executedAt;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Fact {

		private String eventCode;

		private String billingStatus;

		private String billingReasonCode;

		private BigDecimal spendAmount;

		private LocalDateTime receivedAt;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CallbackFact {

		private String eventCode;

		private String billingStatus;

		private String billingReasonCode;

		private BigDecimal spendAmount;

		private BigDecimal currencyAmount;

		private String deductionDecision;

		private BigDecimal deductionRatio;

		private LocalDateTime deductionDecidedAt;

		private LocalDateTime receivedAt;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Flow {

		private String resultStatus;

		private Integer partnerHttpStatus;

		private String message;

		private long durationMs;

		private LocalDateTime eventTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TimelineItem {

		private String stage;

		private String status;

		private LocalDateTime occurredAt;

		private Long durationMs;

		private String detail;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Result {

		private String requestId;

		private String state;

		private Fact fact;

		private Flow flow;

		private CallbackFact callbackFact;

		private Flow callbackFlow;

		private Flow downstreamFlow;

		private String conclusion;

		private List<TimelineItem> timeline;

	}

}
