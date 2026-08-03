package com.yzytech.scx.ocpx.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class CallbackTokenServiceTest {

	@Test
	void issuesOpaqueSignedReferenceAndDetectsTampering() {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		properties.setCallbackTokenKey("a-test-key-that-is-long-enough");
		CallbackTokenService tokens = new CallbackTokenService(properties);

		String token = tokens.issue("callback-ref-1");

		assertThat(token).doesNotContain("FeedbackReq", "device_id");
		assertThat(tokens.verify(token)).isEqualTo("callback-ref-1");
		assertThatThrownBy(() -> tokens.verify(token + "tampered")).isInstanceOf(OcpxException.class)
			.hasMessageContaining("signature");
	}

	@Test
	void rejectsExpiredReference() {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		properties.setCallbackTokenKey("a-test-key-that-is-long-enough");
		properties.setCallbackTokenTtl(Duration.ofSeconds(-1));
		CallbackTokenService tokens = new CallbackTokenService(properties);
		String token = tokens.issue("expired-ref");

		assertThatThrownBy(() -> tokens.verify(token)).isInstanceOf(OcpxException.class)
			.hasMessageContaining("expired");
	}

}
