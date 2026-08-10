package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteConfigurationRepositoryTest {

	private final ProtocolCrypto crypto = mock(ProtocolCrypto.class);

	private final RouteConfigurationRepository repository = new RouteConfigurationRepository(mock(JdbcTemplate.class),
			crypto);

	@Test
	void decryptsTheCredentialRevisionFrozenByThePublishedRoute() {
		when(crypto.decrypt("ciphertext")).thenReturn("runtime-secret");

		assertThat(repository.resolveUpstreamSecret("ciphertext", "ENCRYPTED", "ACTIVE", null, "stale-legacy"))
			.isEqualTo("runtime-secret");
		verify(crypto).decrypt("ciphertext");
	}

	@Test
	void retainsLegacyCompatibilityWithoutReturningItFromManagementApis() {
		assertThat(repository.resolveUpstreamSecret(null, "LEGACY", "ACTIVE", null, "legacy-runtime-secret"))
			.isEqualTo("legacy-runtime-secret");
	}

	@Test
	void failsClosedForIncompleteEncryptedRevision() {
		assertThatThrownBy(
				() -> repository.resolveUpstreamSecret(null, "ENCRYPTED", "ACTIVE", null, "legacy-runtime-secret"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsExpiredOverlapRevisionUntilTheRouteIsRepublished() {
		assertThatThrownBy(() -> repository.resolveUpstreamSecret("ciphertext", "ENCRYPTED", "OVERLAP",
				LocalDateTime.now().minusMinutes(1), null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsADecryptedNonOffSnapshotBeforeItCanEnterTheRuntimeCache() {
		when(crypto.decrypt("tampered-ciphertext")).thenReturn("{\"route\":{\"rtaMode\":\"ACTIVE\"}}");

		assertThatThrownBy(() -> repository.decodeSnapshot("tampered-ciphertext"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("immutable route snapshot cannot be decoded")
			.hasRootCauseMessage("ADX_RTA_BLOCKED_BY_ADR: route snapshot RTA mode must be OFF");
	}

}
