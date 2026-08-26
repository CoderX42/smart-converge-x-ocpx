package com.yzytech.scx.ocpx.callback.web.renrenvideo;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RenrenVideoCallbackAdapterTest {

	private final OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v2",
			Map.of("v1", "renren-video-previous-key", "v2", "renren-video-current-key"), List.of("v1", "v2"));

	private final RenrenVideoCallbackAdapter adapter = new RenrenVideoCallbackAdapter(crypto);

	@ParameterizedTest
	@CsvSource({ "activate,ACTIVATE", "register,REGISTER", "leave,NEXT_STAY", "pay,PAYMENT",
			"first_activate,FIRST_ACTIVATION" })
	void restoresAuthenticatedRouteAndMapsAllPartnerEvents(String event, ActionTypeEnum expected) {
		String ext = crypto.encryptCallback(JSON.toJSONString(feedback()));

		var callback = adapter.toCallback(request("v2", ext, event));

		assertThat(callback.getActionType()).isEqualTo(expected);
		assertThat(callback.getPartnerActionType()).isEqualTo(event);
		assertThat(callback.getCurrencyAmount()).isNull();
		assertThat(callback.getFeedback().getReqId()).isEqualTo("request-id");
		assertThat(callback.getFeedback().getCallbackUrl()).isEqualTo("https://media.example/callback");
	}

	@Test
	void acceptsAStillConfiguredKeyVersion() {
		String clearText = JSON.toJSONString(feedback());
		OcpxKeyRingCrypto.SealedValue sealed = crypto.encrypt("v1",
				clearText.getBytes(java.nio.charset.StandardCharsets.UTF_8), OcpxKeyRingCrypto.PURPOSE_CALLBACK,
				"callback|v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		byte[] packed = java.nio.ByteBuffer.allocate(sealed.nonce().length + sealed.cipherText().length)
			.put(sealed.nonce())
			.put(sealed.cipherText())
			.array();
		String ext = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packed);

		assertThat(adapter.toCallback(request("v1", ext, "activate")).getActionType())
			.isEqualTo(ActionTypeEnum.ACTIVATE);
	}

	@Test
	void rejectsUnknownEventKeyVersionAndTamperedCipherText() {
		String ext = crypto.encryptCallback(JSON.toJSONString(feedback()));

		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("v2", ext, "unknown")));
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("missing", ext, "activate")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> adapter.toCallback(request("v2", ext.substring(0, ext.length() - 1) + "A", "activate")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> adapter.toCallback(request("v2", "x".repeat(12001), "activate")));
	}

	private FeedbackReq feedback() {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.callbackUrl("https://media.example/callback")
			.build();
	}

	private RenrenVideoCallbackRequest request(String kid, String ext, String event) {
		RenrenVideoCallbackRequest request = new RenrenVideoCallbackRequest();
		request.setKid(kid);
		request.setExt(ext);
		request.setEvent(event);
		return request;
	}

}
