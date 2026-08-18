package com.yzytech.scx.ocpx.callback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.fact.service.OcpxEventFactService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackServiceImplTest {

	@Test
	void returnsDeductedWhenAtomicRatioDecisionDropsTheCallback() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.deductionDecisionService.decide("fact-1", "link-id", "media-id", 0)).thenReturn(
				new DeductionDecisionService.Result(DeductionDecision.DEDUCTED, new BigDecimal("0.500000"), false));

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.DEDUCTED);
		verify(fixture.flowLogWriter).callback(fixture.request, true, "CALLBACK_DEDUCTED");
		verify(fixture.eventFactService).finalizeDeduction("fact-1", new BigDecimal("0.500000"),
				DeductionDecision.DEDUCTED);
		verify(fixture.deductionDecisionService).complete("fact-1", "link-id", "media-id");
	}

	@Test
	void returnsNotApplicableForAnEmptyAction() {
		Fixture fixture = fixture(null);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.eventFactService, never()).recordCallback(any());
		verify(fixture.flowLogWriter).callback(fixture.request, false, "CALLBACK_NOT_APPLICABLE");
	}

	@Test
	void returnsNotApplicableForADuplicateWithoutQueuingAgain() {
		Fixture fixture = fixture(ActionTypeEnum.PAYMENT);
		when(fixture.eventFactService.recordCallback(any()))
			.thenReturn(new OcpxEventFactService.CallbackFactResult(false, false, "fact-1", "PASSED"));

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isTrue();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.lists, never()).rightPush(anyString(), anyString());
		verify(fixture.flowLogWriter).callback(fixture.request, true, "CALLBACK_DUPLICATE");
	}

	@Test
	void returnsForwardedWhenTheAtomicDecisionPasses() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.FORWARDED);
		verify(fixture.deductionDecisionService).complete("fact-1", "link-id", "media-id");
		verify(fixture.flowLogWriter).callback(fixture.request, true, "DOWNSTREAM_DELIVERY_SUBMITTED");
	}

	@Test
	void recordsAProcessingFailureWithoutHidingTheOriginalException() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.eventFactService.recordCallback(any())).thenThrow(new IllegalStateException("fact failed"));

		assertThatThrownBy(() -> fixture.service.handleCallback(fixture.request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("fact failed");
		verify(fixture.flowLogWriter).callback(fixture.request, false, "CALLBACK_PROCESSING_FAILED");
	}

	@Test
	void handlesFirstOrderWithoutOverflow() {
		Fixture fixture = fixture(ActionTypeEnum.FIRST_ORDER);
		fixture.request.setPartnerActionType("firstorder");

		assertThat(fixture.service.handleCallback(fixture.request).decision()).isEqualTo(CallbackDecision.FORWARDED);
		var payload = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(fixture.lists).rightPush(anyString(), payload.capture());
		LogData logData = JSON.parseObject(payload.getValue(), LogData.class);
		assertThat(logData.getFirstOrder()).isEqualTo(1);
		assertThat(logData.getStandardActionType()).isEqualTo(19);
		assertThat(logData.getPartnerActionType()).isEqualTo("firstorder");
		verify(fixture.deductionDecisionService).decide("fact-1", "link-id", "media-id", 18);
	}

	@Test
	void queuesPurchaseAmountFidelity() {
		Fixture fixture = fixture(ActionTypeEnum.PAYMENT);
		fixture.request.setPartnerActionType("purchase");
		fixture.request.setCurrencyAmount(new BigDecimal("123.450000"));

		assertThat(fixture.service.handleCallback(fixture.request).decision()).isEqualTo(CallbackDecision.FORWARDED);
		var payload = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(fixture.lists).rightPush(anyString(), payload.capture());
		LogData logData = JSON.parseObject(payload.getValue(), LogData.class);
		assertThat(logData.getPayment()).isEqualTo(1);
		assertThat(logData.getStandardActionType()).isEqualTo(5);
		assertThat(logData.getPartnerActionType()).isEqualTo("purchase");
		assertThat(logData.getCurrencyAmount()).isEqualByComparingTo("123.450000");
	}

	@SuppressWarnings("unchecked")
	private Fixture fixture(ActionTypeEnum actionType) {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		when(redis.opsForList()).thenReturn(lists);
		DeductionDecisionService deductionDecisionService = mock(DeductionDecisionService.class);
		OcpxEventFactService eventFactService = mock(OcpxEventFactService.class);
		OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);
		when(eventFactService.recordCallback(any()))
			.thenReturn(new OcpxEventFactService.CallbackFactResult(true, false, "fact-1", "PENDING"));
		when(deductionDecisionService.decide(anyString(), anyString(), anyString(), any(Integer.class))).thenReturn(
				new DeductionDecisionService.Result(DeductionDecision.PASSED, new BigDecimal("1.000000"), false));
		when(eventFactService.finalizeDeduction(anyString(), any(BigDecimal.class), any(DeductionDecision.class)))
			.thenReturn(true);
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-1")
			.build();
		CallbackReq request = CallbackReq.builder().feedback(feedback).actionType(actionType).actionTime(1L).build();
		return new Fixture(new CallbackServiceImpl(redis, 1, deductionDecisionService, eventFactService, flowLogWriter),
				deductionDecisionService, eventFactService, flowLogWriter, lists, request);
	}

	private record Fixture(CallbackServiceImpl service, DeductionDecisionService deductionDecisionService,
			OcpxEventFactService eventFactService, OcpxFlowLogWriter flowLogWriter,
			ListOperations<String, String> lists, CallbackReq request) {
	}

}
