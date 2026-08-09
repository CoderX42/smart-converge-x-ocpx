package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;

public interface BillingPort {

	BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency, String mode);

	default BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency,
			String mode, String debugExecutionId) {
		return charge(chargeEventId, advertiserId, amount, currency, mode);
	}

	record BillingResult(String status, String reason, BigDecimal balanceAfter) {
		public static BillingResult disabled() {
			return new BillingResult("DISABLED", "BILLING_OFF", null);
		}

		public boolean permitsDelivery() {
			return !"REJECTED".equals(status);
		}

		public boolean posted() {
			return "POSTED".equals(status);
		}
	}

}
