package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.BillingPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DisabledBillingPort implements BillingPort {

	@Override
	public BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency) {
		return BillingResult.disabled();
	}

}
