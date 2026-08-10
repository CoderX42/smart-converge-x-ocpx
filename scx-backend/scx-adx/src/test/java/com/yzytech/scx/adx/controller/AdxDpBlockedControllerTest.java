package com.yzytech.scx.adx.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.common.core.util.Result;
import org.junit.jupiter.api.Test;

class AdxDpBlockedControllerTest {

	@Test
	void convertIsAnExplicitPayloadFreeFailClosedBoundary() {
		Result<Void> result = new AdxDpBlockedController().convert();

		assertThat(result.getCode()).isEqualTo(409);
		assertThat(result.getMsg())
			.isEqualTo("ADX_DP_BLOCKED_BY_DECISION: supplier protocol and security decision are not accepted");
		assertThat(result.getData()).isNull();
	}

}
