package com.yzytech.scx.ocpx.callback.web.weifeng;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeifengCallbackAdapterTest {

	private static final String AES_KEY = "weifeng-test-key";

	private final WeifengCallbackAdapter adapter = new WeifengCallbackAdapter();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(adapter, "callbackAesKey", AES_KEY);
	}

	@Test
	void mapsAllDocumentedEvents() {
		assertThat(callback("ACTIVATED", "__AMOUNT__", "__DAYS__").getActionType()).isEqualTo(ActionTypeEnum.ACTIVATE);
		assertThat(callback("REGISTERED", null, null).getActionType()).isEqualTo(ActionTypeEnum.REGISTER);
		assertThat(callback("PAID", "123.45", null).getActionType()).isEqualTo(ActionTypeEnum.PAYMENT);
		assertThat(callback("RETAINED", null, "7").getActionType()).isEqualTo(ActionTypeEnum.NEXT_STAY);
		assertThat(callback("FIRST_DAY_PAID", "6.50", null).getActionType()).isEqualTo(ActionTypeEnum.FIRST_ORDER);
		assertThat(callback("APP_OPEN", null, null).getActionType()).isEqualTo(ActionTypeEnum.EVOKE);
	}

	@Test
	void preservesPartnerEventAndPaymentAmount() {
		CallbackReq callback = callback("paid", "123.450000", "__DAYS__");

		assertThat(callback.getPartnerActionType()).isEqualTo("PAID");
		assertThat(callback.getCurrencyAmount()).isEqualByComparingTo("123.450000");
		assertThat(callback.getFeedback().getReqId()).isEqualTo("request-id");
		assertThat(callback.getActionTime()).isPositive();
	}

	@Test
	void rejectsUnknownEventsInvalidAmountsDaysAndRouteContext() {
		assertThatThrownBy(() -> callback("UNKNOWN", null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> callback("PAID", "-1", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> callback("ACTIVATED", "1", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> callback("RETAINED", null, "3651")).isInstanceOf(IllegalArgumentException.class);

		WeifengCallbackRequest request = request("ACTIVATED", null, null);
		request.setExt("invalid");
		assertThatThrownBy(() -> adapter.toCallback(request)).isInstanceOf(IllegalArgumentException.class);
	}

	private CallbackReq callback(String event, String amount, String days) {
		return adapter.toCallback(request(event, amount, days));
	}

	private WeifengCallbackRequest request(String event, String amount, String days) {
		WeifengCallbackRequest request = new WeifengCallbackRequest();
		request.setExt(AesUtil.encryptBase64(JSON.toJSONString(feedback()), AES_KEY));
		request.setS("link-id_task-id");
		request.setEvent(event);
		request.setAmount(amount);
		request.setDays(days);
		return request;
	}

	private FeedbackReq feedback() {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.build();
	}

}
