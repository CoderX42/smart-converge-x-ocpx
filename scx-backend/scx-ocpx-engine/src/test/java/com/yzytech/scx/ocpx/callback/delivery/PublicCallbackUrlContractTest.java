package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.contract.PublicCallbackUrlContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCallbackUrlContractTest {

	private final PublicCallbackUrlContract contract = new PublicCallbackUrlContract();

	@Test
	void acceptsPublicHttpUrlAndAppendsFormalCallbackFields() {
		String callbackUrl = contract.requirePublicHttpUrl("https://8.8.8.8/callback?click_id=uat");

		assertThat(contract.appendCallbackFields(callbackUrl, 18, new BigDecimal("1.250000")))
			.isEqualTo("https://8.8.8.8/callback?click_id=uat&event_type=18&currency_amount=1.250000");
	}

	@Test
	void rejectsLoopbackAndUserInfo() {
		assertThatThrownBy(() -> contract.requirePublicHttpUrl("http://127.0.0.1/callback"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> contract.requirePublicHttpUrl("https://user@example.com/callback"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
