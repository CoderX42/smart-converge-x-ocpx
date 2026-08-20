package com.yzytech.scx.ocpx.callback.web.gravity;

import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GravityCallbackAdapterTest {

	@Test
	void restoresClickRouteAndMapsConfirmedV1EventToActivate() {
		GravityCallbackContextCodec codec = new GravityCallbackContextCodec("gravity-test-callback-key");
		GravityCallbackAdapter adapter = new GravityCallbackAdapter(codec);
		String token = codec.encode(new GravityCallbackContext(1, ActionTypeEnum.ACTIVATE.name(), "media-id", "task-id",
				"advertiser-id", "link-id", "request-id", "encoded-callback"));

		var callback = adapter.toCallback(token);

		assertThat(callback.getActionType()).isEqualTo(ActionTypeEnum.ACTIVATE);
		assertThat(callback.getFeedback().getReqId()).isEqualTo("request-id");
		assertThat(callback.getFeedback().getCallbackUrl()).isEqualTo("encoded-callback");
		assertThat(callback.getPartnerActionType()).isEqualTo("ATTRIBUTION_SUCCESS");
	}

	@Test
	void rejectsUnknownProtocolVersionOrEvent() {
		GravityCallbackContextCodec codec = new GravityCallbackContextCodec("gravity-test-callback-key");
		GravityCallbackAdapter adapter = new GravityCallbackAdapter(codec);
		String token = codec.encode(new GravityCallbackContext(2, "REGISTER", "media-id", "task-id", "advertiser-id",
				"link-id", "request-id", null));

		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(token));
	}

}
