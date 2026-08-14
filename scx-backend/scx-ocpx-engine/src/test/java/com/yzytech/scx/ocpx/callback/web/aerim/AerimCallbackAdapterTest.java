package com.yzytech.scx.ocpx.callback.web.aerim;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AerimCallbackAdapterTest {

	private static final String AES_KEY = "aerim-test-key16";

	private final AerimCallbackAdapter adapter = new AerimCallbackAdapter();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(adapter, "callbackAesKey", AES_KEY);
	}

	@ParameterizedTest
	@CsvSource({ "activate,ACTIVATE,1", "reactivation,EVOKE,2", "register,REGISTER,4", "purchase,PAYMENT,5",
			"new,CUSTOMER_ACQUISITION,6", "day1retention,NEXT_STAY,7", "completeorder,FINISH_THE_ORDER,11",
			"firstreactivation,FIRST_ACTIVATION,18", "firstorder,FIRST_ORDER,19" })
	void mapsTheNineExactAerimActions(String action, ActionTypeEnum expected, int index) {
		AerimCallbackRequest request = request(action, feedback());
		long before = System.currentTimeMillis();

		var callback = adapter.toCallback(request);

		assertThat(callback.getActionType()).isEqualTo(expected);
		assertThat(callback.getActionType().getIndex()).isEqualTo(index);
		assertThat(callback.getPartnerActionType()).isEqualTo(action);
		assertThat(callback.getActionTime()).isBetween(before, System.currentTimeMillis());
	}

	@Test
	void preservesPurchaseAmountAndRejectsAliasesOrIllegalAmounts() {
		AerimCallbackRequest purchase = request("purchase", feedback());
		purchase.setCurrency_amount(new BigDecimal("123.450000"));
		assertThat(adapter.toCallback(purchase).getCurrencyAmount()).isEqualByComparingTo("123.450000");

		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("active", feedback())));
		AerimCallbackRequest register = request("register", feedback());
		register.setCurrency_amount(BigDecimal.ONE);
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(register));
		AerimCallbackRequest negative = request("purchase", feedback());
		negative.setCurrency_amount(new BigDecimal("-0.01"));
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(negative));
		AerimCallbackRequest excessiveScale = request("purchase", feedback());
		excessiveScale.setCurrency_amount(new BigDecimal("1.0000001"));
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(excessiveScale));
	}

	@Test
	void rejectsInvalidCiphertextAndEachInvalidRouteId() {
		AerimCallbackRequest invalidExt = request("activate", feedback());
		invalidExt.setExt("not-a-valid-ciphertext");
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(invalidExt));

		FeedbackReq missingMedia = feedback();
		missingMedia.setMId(null);
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("activate", missingMedia)));
		FeedbackReq longTask = feedback();
		longTask.setTId("x".repeat(33));
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("activate", longTask)));
		FeedbackReq missingAdvertiser = feedback();
		missingAdvertiser.setAId("");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> adapter.toCallback(request("activate", missingAdvertiser)));
		FeedbackReq missingLink = feedback();
		missingLink.setLId(null);
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.toCallback(request("activate", missingLink)));
	}

	private AerimCallbackRequest request(String action, FeedbackReq feedback) {
		AerimCallbackRequest request = new AerimCallbackRequest();
		request.setExt(AesUtil.encryptBase64(JSON.toJSONString(feedback), AES_KEY));
		request.setS("link-id_task-id");
		request.setAction_type(action);
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
