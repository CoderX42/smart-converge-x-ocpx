package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SummaryReportViews {

	private SummaryReportViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Row {

		private String reportTime;

		private String mediaId;

		private String mediaName;

		private String monitorTaskId;

		private String monitorName;

		private String advertiserId;

		private String advertiserName;

		private String promotionLinkId;

		private String promotionLinkName;

		private String linkRadio;

		private long callbackCount;

		private long deductionPassedCount;

		private long deductionDeductedCount;

		private long deductionPendingCount;

		private long uniShowSmt;

		private long uniClkSmt;

		private Double clickRate;

		private BigDecimal basePrice;

		private long activate;

		private long evoke;

		private long login;

		private long register;

		private long payment;

		private long customerAcquisition;

		private long nextStay;

		private long visits;

		private long authentication;

		private long placeOrder;

		private long finishTheOrder;

		private long firstActivation;

		private long firstOrder;

		private long download;

		private long activity;

		private long addiction;

		private BigDecimal currencyAmount;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page {

		private List<Row> records;

		private long current;

		private long size;

		private long total;

		private LocalDateTime deductionCoverageStart;

		private String deductionCoverageStatus;

	}

}
