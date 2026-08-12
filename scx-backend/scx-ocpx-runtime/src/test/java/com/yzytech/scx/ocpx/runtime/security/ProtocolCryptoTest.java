package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxCryptoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolCryptoTest {

	@Test
	void signsTokensWithDomainSeparatedKeyMaterial() {
		OcpxCryptoProperties properties = new OcpxCryptoProperties();
		properties.setPayloadAesKey("local-test-key-material-only");
		ProtocolCrypto crypto = new ProtocolCrypto(properties);

		String token = crypto.signToken("callback", "payload");
		assertThat(crypto.verifyToken("callback", token)).isEqualTo("payload");
		assertThat(crypto.blindIndex("media-login", "account@example.invalid")).hasSize(64)
			.isEqualTo(crypto.blindIndex("media-login", "account@example.invalid"))
			.isNotEqualTo(crypto.blindIndex("other-purpose", "account@example.invalid"));
	}

}
