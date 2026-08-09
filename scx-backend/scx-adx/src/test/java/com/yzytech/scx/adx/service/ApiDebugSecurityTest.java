package com.yzytech.scx.adx.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiDebugSecurityTest {

	@Test
	void rejectsNonHttpsPrivateAndUnexpectedPorts() throws Exception {
		assertThatThrownBy(() -> ApiDebugService.validatePublicHttps("http://example.com"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HTTPS");
		assertThatThrownBy(() -> ApiDebugService.validatePublicHttps("https://127.0.0.1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("非公网");
		assertThatThrownBy(() -> ApiDebugService.validatePublicHttps("https://127.0.0.1:9443"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("443");
		assertThatThrownBy(() -> ApiDebugService.validatePublicHttps("https://user:pass@example.com"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("用户凭据");

		assertThat(ApiDebugService.isPublic(InetAddress.getByName("10.1.2.3"))).isFalse();
		assertThat(ApiDebugService.isPublic(InetAddress.getByName("169.254.169.254"))).isFalse();
		assertThat(ApiDebugService.isPublic(InetAddress.getByName("224.0.0.1"))).isFalse();
	}

	@Test
	void treatsGeneratedOrExplicitClickFeedbackAsBillable() {
		assertThat(ApiDebugService.isBillable("CPC", "DOWNSTREAM_TO_UPSTREAM", "")).isTrue();
		assertThat(ApiDebugService.isBillable("CPC", "DOWNSTREAM_TO_UPSTREAM", "{\"event\":\"CLICK\"}")).isTrue();
		assertThat(ApiDebugService.isBillable("CPC", "DOWNSTREAM_TO_UPSTREAM", "{\"event\":\"SHOW\"}")).isFalse();
		assertThat(ApiDebugService.isBillable("CPC", "UPSTREAM_TO_DOWNSTREAM", "")).isFalse();
	}

}
