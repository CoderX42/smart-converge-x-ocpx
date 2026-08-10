package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DashboardViews {

	private DashboardViews() {
	}

	public record AccountSummary(String currency, BigDecimal totalBalance, BigDecimal rechargeAmount,
			BigDecimal cpcSpend, BigDecimal cpaSpend, long rejectedCharges, long cpaRejectedCharges,
			LocalDateTime generatedAt) {
	}

}
