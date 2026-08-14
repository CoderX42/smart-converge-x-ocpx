package com.yzytech.scx.ocpx.callback.delivery;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PublicCallbackUrlPolicyTest {

	private final PublicCallbackUrlPolicy policy = new PublicCallbackUrlPolicy();

	@Test
	void blocksLocalPrivateAndDangerousTargets() {
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requirePublicHttpUrl("http://127.0.0.1/cb"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requirePublicHttpUrl("http://10.0.0.1/cb"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requirePublicHttpUrl("http://169.254.1.1/cb"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requirePublicHttpUrl("file:///etc/passwd"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requirePublicHttpUrl("http://user@8.8.8.8/cb"));
	}

	@Test
	void acceptsPublicHttpTargetsAndAppendsEventParameter() {
		String callbackUrl = policy.requirePublicHttpUrl("https://8.8.8.8/callback?id=42");

		assertThat(callbackUrl).isEqualTo("https://8.8.8.8/callback?id=42");
		assertThat(policy.appendEventType(callbackUrl, 2)).isEqualTo("https://8.8.8.8/callback?id=42&event_type=2");
		assertThat(policy.appendEventType("https://8.8.8.8/callback", 2))
			.isEqualTo("https://8.8.8.8/callback?event_type=2");
		assertThat(policy.appendCallbackFields(callbackUrl, 19, new BigDecimal("123.45")))
			.isEqualTo("https://8.8.8.8/callback?id=42&event_type=19&currency_amount=123.45");
	}

}
