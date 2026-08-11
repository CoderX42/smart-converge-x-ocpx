package com.yzytech.scx.ocpx.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.common.core.util.Result;
import org.junit.jupiter.api.Test;

class OcpxDpBlockedControllerTest {

	@Test
	void convertIsAnExplicitPayloadFreeFailClosedBoundary() {
		Result<Void> result = new OcpxDpBlockedController().convert();

		assertThat(result.getCode()).isEqualTo(409);
		assertThat(result.getMsg())
			.isEqualTo("OCPX_DP_BLOCKED_BY_DECISION: supplier protocol and security decision are not accepted");
		assertThat(result.getData()).isNull();
	}

}
