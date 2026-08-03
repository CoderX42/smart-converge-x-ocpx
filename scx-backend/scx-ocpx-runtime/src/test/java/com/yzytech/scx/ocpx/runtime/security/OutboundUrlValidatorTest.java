package com.yzytech.scx.ocpx.runtime.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;

class OutboundUrlValidatorTest {

	@Test
	void productionPolicyRejectsHttpAndPrivateAddresses() {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		properties.setAllowHttp(false);
		properties.setAllowPrivateAddresses(false);
		OutboundUrlValidator validator = new OutboundUrlValidator(properties);

		assertThatThrownBy(() -> validator.validate(URI.create("http://public.example/callback")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HTTPS");
		assertThatThrownBy(() -> validator.validate(URI.create("https://127.0.0.1/callback")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("private");
	}

}
