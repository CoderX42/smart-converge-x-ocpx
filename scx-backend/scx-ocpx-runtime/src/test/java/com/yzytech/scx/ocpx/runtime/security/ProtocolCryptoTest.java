package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolCryptoTest {

	@Test
	void encryptsRoundTripsAndBuildsDomainSeparatedBlindIndexes() {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		properties.setPayloadAesKey("local-test-key-material-only");
		ProtocolCrypto crypto = new ProtocolCrypto(properties);

		String encrypted = crypto.encrypt("account@example.invalid");
		assertThat(encrypted).doesNotContain("account@example.invalid");
		assertThat(crypto.decrypt(encrypted)).isEqualTo("account@example.invalid");
		assertThat(crypto.blindIndex("media-login", "account@example.invalid")).hasSize(64)
			.isEqualTo(crypto.blindIndex("media-login", "account@example.invalid"))
			.isNotEqualTo(crypto.blindIndex("other-purpose", "account@example.invalid"));
	}

}
