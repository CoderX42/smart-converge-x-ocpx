package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AerimEndpointPolicyTest {

	@Test
	void requiresHttpsExactAllowlistAndPublicAddress() {
		AerimProperties properties = new AerimProperties();
		properties.setAllowedHosts(Set.of("8.8.8.8"));
		AerimEndpointPolicy policy = new AerimEndpointPolicy(properties);

		assertThat(policy.requireAllowedHttpsUrl("https://8.8.8.8/ocpx/c?id=1"))
			.isEqualTo("https://8.8.8.8/ocpx/c?id=1");
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpsUrl("http://8.8.8.8/ocpx/c"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpsUrl("https://1.1.1.1/ocpx/c"));
	}

}
