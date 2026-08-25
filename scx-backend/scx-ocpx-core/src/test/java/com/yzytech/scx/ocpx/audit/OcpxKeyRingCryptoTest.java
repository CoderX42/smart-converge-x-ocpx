package com.yzytech.scx.ocpx.audit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcpxKeyRingCryptoTest {

	@Test
	void separatesAuditAndCallbackKeysAndRejectsTampering() {
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("k2", Map.of("k1", "local-dev-key-01", "k2",
				"0123456789abcdef0123456789abcdef"), List.of("k1", "k2"));
		byte[] aad = "audit-1|MEDIA_INGRESS".getBytes(StandardCharsets.UTF_8);
		OcpxKeyRingCrypto.SealedValue sealed = crypto.encrypt("secret".getBytes(StandardCharsets.UTF_8),
				OcpxKeyRingCrypto.PURPOSE_AUDIT, aad);

		assertThat(new String(crypto.decrypt(sealed, OcpxKeyRingCrypto.PURPOSE_AUDIT, aad), StandardCharsets.UTF_8))
			.isEqualTo("secret");
		assertThatThrownBy(() -> crypto.decrypt(sealed, OcpxKeyRingCrypto.PURPOSE_CALLBACK, aad))
			.isInstanceOf(IllegalArgumentException.class);
		sealed.cipherText()[0] ^= 1;
		assertThatThrownBy(() -> crypto.decrypt(sealed, OcpxKeyRingCrypto.PURPOSE_AUDIT, aad))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void roundTripsNewCallbackFormatAndReadsLegacyWithoutKid() {
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("k1", Map.of("k1", "local-dev-key-01"), List.of("k1"));
		String token = crypto.encryptCallback("payload");
		String legacy = com.yzytech.scx.ocpx.audit.TestLegacyAes.encrypt("legacy", "local-dev-key-01");

		assertThat(crypto.decryptCallback("k1", token)).isEqualTo("payload");
		assertThat(crypto.decryptCallback(null, legacy)).isEqualTo("legacy");
	}

}
