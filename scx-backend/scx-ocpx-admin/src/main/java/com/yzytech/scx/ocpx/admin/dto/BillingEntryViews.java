package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class BillingEntryViews {

	private BillingEntryViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {
		private String id;
		private String advertiserId;
		private String advertiserName;
		private String monitorTaskId;
		private String monitorTaskName;
		private String eventFactId;
		private String entryType;
		private String status;
		private BigDecimal amount;
		private BigDecimal balanceBefore;
		private BigDecimal balanceAfter;
		private String businessKey;
		private String reasonCode;
		private String operatorUserId;
		private String remark;
		private LocalDateTime occurredAt;
	}

}
