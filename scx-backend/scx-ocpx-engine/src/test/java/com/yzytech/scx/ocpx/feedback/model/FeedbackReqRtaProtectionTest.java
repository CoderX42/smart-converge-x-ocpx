package com.yzytech.scx.ocpx.feedback.model;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackReqRtaProtectionTest {

	@Test
	void keepsACleanCallbackUrlAndCarriesOnlyTheAuthenticatedMarker() {
		FeedbackReq original = FeedbackReq.builder()
			.idfa("raw-idfa")
			.imei("raw-imei")
			.callbackUrl("https%3A%2F%2Fmedia.example%2Fcallback%3Fclick_id%3D42")
			.build();

		FeedbackReq copy = original.copyForRtaDownstream();

		assertThat(copy.getCallbackUrl()).isEqualTo(original.getCallbackUrl());
		assertThat(copy.getIdfa()).isNull();
		assertThat(copy.getImei()).isNull();
		String encoded = JSON.toJSONString(copy);
		assertThat(encoded).contains("\"rta_protected\":true");
		FeedbackReq restored = JSON.parseObject(encoded, FeedbackReq.class);
		assertThat(restored.isRtaProtected()).isTrue();
		assertThat(restored.getIdfa()).isNull();
	}

	@Test
	void dropsCallbackUrlWhenItContainsAnInboundRawIdentifier() {
		FeedbackReq original = FeedbackReq.builder()
			.idfa("raw-idfa")
			.callbackUrl("https%3A%2F%2Fmedia.example%2Fcallback%3Fidfa%3Draw-idfa")
			.build();

		assertThat(original.copyForRtaDownstream().getCallbackUrl()).isNull();
	}

	@Test
	void doesNotAddMarkerToOrdinaryFeedbackJson() {
		FeedbackReq original = FeedbackReq.builder().idfa("raw-idfa").build();

		assertThat(JSON.toJSONString(original)).doesNotContain("rta_protected");
	}

}
