package com.yzytech.scx.ocpx.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxUrlPolicyTest {

	private final OcpxUrlPolicy policy = new OcpxUrlPolicy();

	@Test
	void acceptsPublicHttpTemplateWithSupportedMacros() {
		OcpxUrlPolicy.Result result = policy
			.validate("https://8.8.8.8/path?request=__REQUEST_ID__&device=__OAID__");

		assertThat(result.status()).isEqualTo("PASS");
		assertThat(result.normalizedUrl())
			.isEqualTo("https://8.8.8.8/path?request=__REQUEST_ID__&device=__OAID__");
	}

	@Test
	void rejectsPrivateDangerousAndUnknownMacroUrls() {
		assertThat(policy.validate("http://127.0.0.1/callback").status()).isEqualTo("BLOCK");
		assertThat(policy.validate("http://10.0.0.1/callback").status()).isEqualTo("BLOCK");
		assertThat(policy.validate("file:///etc/passwd").status()).isEqualTo("BLOCK");
		assertThat(policy.validate("https://ads.example.com/?x=__RAW_SECRET__").status()).isEqualTo("BLOCK");
	}

}
