package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public final class SummaryReportViews {

	private SummaryReportViews() {
	}

	public record Row(String reportTime, String mediaId, String mediaName, String taskId, String taskName,
			String advertiserId, String advertiserName, String promotionLinkId, String promotionLinkName,
			String linkRadio, long uniShowSmt, long uniClkSmt, Double clickRate, BigDecimal basePrice, long activate,
			long evoke, long login, long register, long payment, long customerAcquisition, long nextStay, long visits,
			long authentication, long placeOrder, long finishTheOrder) {
	}

	public record Page(List<Row> records, long current, long size, long total) {
	}

}
