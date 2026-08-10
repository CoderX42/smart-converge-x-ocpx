package com.yzytech.scx.ocpx.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RouteSnapshotRtaFailClosedTest {

	@Test
	void acceptsOnlyOffWhileRtaDecisionIsBlocked() {
		assertEquals("OFF", route("OFF").rtaMode());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> route("ACTIVE"));
		assertEquals("ADX_RTA_BLOCKED_BY_ADR: route snapshot RTA mode must be OFF", error.getMessage());
	}

	private RouteSnapshot route(String rtaMode) {
		return new RouteSnapshot("route-1", "biz-1", 1, "channel-1", "channel", "STANDARD", "HMAC", 1, "channel-secret",
				"advertiser-1", "link-1", "STANDARD", "upstream-secret", "https://upstream.example.invalid", "",
				"https://callback.example.invalid", BigDecimal.ZERO, BigDecimal.ONE, rtaMode, "OFF");
	}

}
