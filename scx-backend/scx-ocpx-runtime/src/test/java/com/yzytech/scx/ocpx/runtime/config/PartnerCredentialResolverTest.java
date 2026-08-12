package com.yzytech.scx.ocpx.runtime.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerCredentialResolverTest {

	@Test
	void resolvesOnlyConfiguredMatchingVersion() {
		PartnerCredentialProperties properties = new PartnerCredentialProperties();
		PartnerCredentialProperties.Credential credential = credential(3, "account", "secret-value");
		properties.setAdvertisers(Map.of("adv-main", credential));
		PartnerCredentialResolver resolver = new PartnerCredentialResolver(properties);

		assertThat(resolver.requireAdvertiser("adv-main", 3).account()).isEqualTo("account");
		assertThatThrownBy(() -> resolver.requireAdvertiser("missing", 3)).hasMessageContaining("not configured");
		assertThatThrownBy(() -> resolver.requireAdvertiser("adv-main", 2)).hasMessageContaining("version mismatch");
	}

	@Test
	void mediaWithoutAuthenticationNeedsNoCredential() {
		PartnerCredentialResolver.ResolvedCredential resolved = new PartnerCredentialResolver(
				new PartnerCredentialProperties()).requireMedia("NONE", null, 0);

		assertThat(resolved.version()).isZero();
		assertThat(resolved.secret()).isNull();
	}

	private PartnerCredentialProperties.Credential credential(long version, String account, String secret) {
		PartnerCredentialProperties.Credential credential = new PartnerCredentialProperties.Credential();
		credential.setVersion(version);
		credential.setAccount(account);
		credential.setSecret(secret);
		return credential;
	}

}
