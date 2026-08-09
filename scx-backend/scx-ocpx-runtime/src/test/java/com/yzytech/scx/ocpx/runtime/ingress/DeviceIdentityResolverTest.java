package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceIdentityResolverTest {

	private DeviceIdentityResolver resolver;

	@BeforeEach
	void setUp() {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		properties.setDeviceHmacKey("test-device-hmac-key-at-least-32-bytes");
		properties.setDeviceHmacKeyVersion(2);
		resolver = new DeviceIdentityResolver(properties);
	}

	@Test
	void androidUsesOaidBeforeOtherAndroidIdentifiers() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setOs("Android");
		feedback.setOaid("OAID-ONE");
		feedback.setGaid("GAID-TWO");

		var identity = resolver.resolve(feedback);

		assertThat(identity.platform()).isEqualTo("ANDROID");
		assertThat(identity.idType()).isEqualTo("OAID");
		assertThat(identity.keyVersion()).isEqualTo(2);
		assertThat(identity.keyHash()).matches("[0-9a-f]{64}");
	}

	@Test
	void iosUsesIdfaAndNormalizesCase() {
		FeedbackReqV1 upper = new FeedbackReqV1();
		upper.setOs("iOS");
		upper.setIdfa("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");
		FeedbackReqV1 lower = new FeedbackReqV1();
		lower.setOs("iphone");
		lower.setIdfa("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

		assertThat(resolver.resolve(upper).platform()).isEqualTo("IOS");
		assertThat(resolver.resolve(upper).idType()).isEqualTo("IDFA");
		assertThat(resolver.resolve(upper).keyHash()).isEqualTo(resolver.resolve(lower).keyHash());
	}

	@Test
	void missingOrAmbiguousPlatformDoesNotCreateAFingerprint() {
		FeedbackReqV1 missing = new FeedbackReqV1();
		FeedbackReqV1 mixed = new FeedbackReqV1();
		mixed.setOaid("android-device");
		mixed.setIdfa("ios-device");

		assertThat(resolver.resolve(missing)).isNull();
		assertThat(resolver.resolve(mixed)).isNull();
	}
}
