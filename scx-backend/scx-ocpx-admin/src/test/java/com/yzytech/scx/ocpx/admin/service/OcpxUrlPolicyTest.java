package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OcpxUrlPolicyTest {

	private final OcpxUrlPolicy policy = new OcpxUrlPolicy();

	@Test
	void normalizesPublicHttpUrlsWithoutResolvingOrOpeningThem() {
		OcpxUrlPolicy.Result result = policy
			.validate(" HTTPS://PUBLIC.EXAMPLE.COM:443/a/../click?oaid=__OAID__&click=__CLICKID__ ");

		assertThat(result.status()).isEqualTo("PASS");
		assertThat(result.normalizedUrl())
			.isEqualTo("https://public.example.com/click?oaid=__OAID__&click=__CLICKID__");
		assertThat(result.maskedUrl()).isEqualTo("https://public.example.com:443/…?query=•••");
		assertThat(result.macros()).containsExactly("__OAID__", "__CLICKID__");
		assertThat(result.fingerprint()).hasSize(64);
	}

	@Test
	void rejectsCredentialsPrivateReservedAndAmbiguousHosts() {
		assertThat(policy.validate("https://user:pass@public.example.com/path").issues())
			.contains("URL_USERINFO_FORBIDDEN");
		assertThat(policy.validate("https://127.0.0.1/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://127.1/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://2130706433/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://0177.0.0.1/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://0x7f.0.0.1/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://10.1.2.3/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://[::1]/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("https://service.internal/path").issues()).contains("URL_HOST_NOT_PUBLIC");
		assertThat(policy.validate("file:///tmp/value").issues()).contains("URL_SCHEME_NOT_ALLOWED");
	}

	@Test
	void rejectsDuplicateQueryKeysAndUnknownMacrosWithoutFilteringThem() {
		OcpxUrlPolicy.Result duplicate = policy.validate("https://public.example.com/click?id=1&id=2");
		assertThat(duplicate.status()).isEqualTo("BLOCK");
		assertThat(duplicate.issues()).contains("URL_QUERY_KEY_DUPLICATE:id");

		OcpxUrlPolicy.Result unknown = policy.validate("https://public.example.com/click?x=__UNKNOWN__");
		assertThat(unknown.status()).isEqualTo("BLOCK");
		assertThat(unknown.issues()).contains("URL_MACRO_NOT_ALLOWED:__UNKNOWN__");
		assertThat(unknown.normalizedUrl()).isNull();
	}

}
