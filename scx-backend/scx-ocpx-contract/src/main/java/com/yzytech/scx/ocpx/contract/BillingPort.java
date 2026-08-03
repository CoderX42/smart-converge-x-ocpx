package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;

public interface BillingPort {

	BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency);

	record BillingResult(String status, String reason) {
		public static BillingResult disabled() {
			return new BillingResult("DISABLED", "BILLING_OFF");
		}
	}

}
