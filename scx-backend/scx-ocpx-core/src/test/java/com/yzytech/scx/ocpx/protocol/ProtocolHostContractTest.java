package com.yzytech.scx.ocpx.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProtocolHostContractTest {

	@Test
	void normalizesExactDomainsAndRejectsUrlWildcardIpAndSingleLabelValues() {
		assertThat(ProtocolHostContract.normalizeConfiguredHost(" Track.Example.COM. ")).isEqualTo("track.example.com");

		for (String invalid : new String[] { "https://track.example.com", "*.example.com", "127.0.0.1",
				"track.example.com:443", "localhost", "track.example.com/path" }) {
			assertThatIllegalArgumentException().as(invalid)
				.isThrownBy(() -> ProtocolHostContract.normalizeConfiguredHost(invalid));
		}
	}

}
