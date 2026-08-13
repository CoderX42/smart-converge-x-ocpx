package com.yzytech.scx.ocpx.callback.web.kimi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KimiCallbackAdapterTest {

	private final KimiCallbackAdapter adapter = adapter();

	@Test
	void decryptsTheExtAndRestoresTheOriginalFeedback() {
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-1")
			.build();
		KimiCallbackReq request = new KimiCallbackReq();
		request.setExt(AesUtil.encryptBase64(JSON.toJSONString(feedback), "local-dev-key-01"));
		request.setS("link-id_media-id");
		request.setAction_type("activation");
		request.setAction_time(1700000000123L);

		CallbackReq result = adapter.toCallback(request);

		assertThat(result.getActionType()).isEqualTo(ActionTypeEnum.ACTIVATE);
		assertThat(result.getActionTime()).isEqualTo(1700000000123L);
		assertThat(result.getFeedback().getMId()).isEqualTo("media-id");
		assertThat(result.getFeedback().getTId()).isEqualTo("task-id");
		assertThat(result.getFeedback().getAId()).isEqualTo("advertiser-id");
		assertThat(result.getFeedback().getLId()).isEqualTo("link-id");
		assertThat(result.getFeedback().getReqId()).isEqualTo("request-1");
	}

	@Test
	void mapsKimiActionsAndKeepsUnknownOnesNull() {
		FeedbackReq feedback = FeedbackReq.builder().reqId("request-1").build();
		String ext = AesUtil.encryptBase64(JSON.toJSONString(feedback), "local-dev-key-01");
		KimiCallbackReq request = new KimiCallbackReq();
		request.setExt(ext);
		request.setAction_time(1L);

		request.setAction_type("activation");
		assertThat(adapter.toCallback(request).getActionType()).isEqualTo(ActionTypeEnum.ACTIVATE);
		request.setAction_type("retention_2");
		assertThat(adapter.toCallback(request).getActionType()).isEqualTo(ActionTypeEnum.NEXT_STAY);
		request.setAction_type("pay");
		assertThat(adapter.toCallback(request).getActionType()).isNull();
	}

	@Test
	void rejectsAnExtThatCannotBeDecrypted() {
		KimiCallbackReq request = new KimiCallbackReq();
		request.setExt("not-a-valid-ciphertext");
		request.setAction_type("activation");
		request.setAction_time(1L);

		assertThatThrownBy(() -> adapter.toCallback(request)).isInstanceOf(NullPointerException.class);
	}

	private KimiCallbackAdapter adapter() {
		KimiCallbackAdapter adapter = new KimiCallbackAdapter();
		ReflectionTestUtils.setField(adapter, "callbackAesKey", "local-dev-key-01");
		return adapter;
	}

}
