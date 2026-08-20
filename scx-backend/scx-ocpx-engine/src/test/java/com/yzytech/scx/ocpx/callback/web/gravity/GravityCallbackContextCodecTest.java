package com.yzytech.scx.ocpx.callback.web.gravity;

import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GravityCallbackContextCodecTest {

	@Test
	void roundTripsUrlSafeAuthenticatedContextAndRejectsTampering() {
		GravityCallbackContextCodec codec = new GravityCallbackContextCodec("gravity-test-callback-key");
		GravityCallbackContext original = new GravityCallbackContext(1, ActionTypeEnum.ACTIVATE.name(), "media-id",
				"task-id", "advertiser-id", "link-id", "request-id", "https%3A%2F%2Fmedia.example%2Fcallback");

		String token = codec.encode(original);

		assertThat(token).matches("[A-Za-z0-9_-]+");
		assertThat(codec.decode(token)).isEqualTo(original);
		int position = token.length() / 2;
		char replacement = token.charAt(position) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, position) + replacement + token.substring(position + 1);
		assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(tampered));
	}

}
