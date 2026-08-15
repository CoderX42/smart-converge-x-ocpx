package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AerimEndpointPolicyTest {

	@Test
	void allowsHttpAndHttpsOnlyForTheExactPublicAllowlistedHost() {
		AerimProperties properties = new AerimProperties();
		properties.setAllowedHosts(Set.of("8.8.8.8"));
		AerimEndpointPolicy policy = new AerimEndpointPolicy(properties);

		assertThat(policy.requireAllowedHttpUrl("https://8.8.8.8/ocpx/c?id=1"))
			.isEqualTo("https://8.8.8.8/ocpx/c?id=1");
		assertThat(policy.requireAllowedHttpUrl("http://8.8.8.8/ocpx/c?id=2"))
			.isEqualTo("http://8.8.8.8/ocpx/c?id=2");
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpUrl("ftp://8.8.8.8/ocpx/c"));
		assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpUrl("http://1.1.1.1/ocpx/c"));
	}

}
