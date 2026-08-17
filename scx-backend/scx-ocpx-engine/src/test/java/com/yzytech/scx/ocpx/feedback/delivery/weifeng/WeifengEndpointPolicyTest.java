package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeifengEndpointPolicyTest {

	@Test
	void rejectsUnknownHostsWrongPathsAndLocalAddresses() {
		WeifengProperties properties = new WeifengProperties();
		properties.setAllowedHosts(Set.of("ad.adapi.cn"));
		WeifengEndpointPolicy policy = new WeifengEndpointPolicy(properties);

		assertThatThrownBy(() -> policy.requireAllowedClickUrl("https://example.com/click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy.requireAllowedClickUrl("http://ad.adapi.cn/not-click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);

		properties.setAllowedHosts(Set.of("127.0.0.1"));
		assertThatThrownBy(() -> policy.requireAllowedClickUrl("http://127.0.0.1/click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
