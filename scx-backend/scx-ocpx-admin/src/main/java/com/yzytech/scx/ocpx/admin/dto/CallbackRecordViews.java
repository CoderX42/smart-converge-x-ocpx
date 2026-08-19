package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CallbackRecordViews {

	private CallbackRecordViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Row {

		private String id;

		private String requestId;

		private String eventCode;

		private boolean targetConversion;

		private String advertiserId;

		private String advertiserName;

		private String promotionLinkId;

		private String promotionLinkName;

		private String mediaId;

		private String mediaName;

		private String monitorTaskId;

		private String monitorTaskName;

		private String billingStatus;

		private String billingReasonCode;

		private BigDecimal spendAmount;

		private BigDecimal currencyAmount;

		private String deductionDecision;

		private BigDecimal deductionRatioSnapshot;

		private LocalDateTime deductionDecidedAt;

		private LocalDateTime receivedAt;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page {

		private List<Row> records;

		private long current;

		private long size;

		private long total;

	}

}
