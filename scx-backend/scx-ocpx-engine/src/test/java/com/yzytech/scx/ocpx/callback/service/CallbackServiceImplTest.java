package com.yzytech.scx.ocpx.callback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackServiceImplTest {

	@Test
	void returnsDeductedWhenLegacyRatioDropsTheCallback() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.helper.getRouteRatio("link-id", "media-id")).thenReturn(0.5);
		when(fixture.helper.getRouteOriginalCounts("link-id", "media-id")).thenReturn(new int[11]);
		when(fixture.helper.getRouteDeliveredCounts("link-id", "media-id")).thenReturn(new int[11]);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.DEDUCTED);
	}

	@Test
	void returnsNotApplicableForAnEmptyAction() {
		Fixture fixture = fixture(null);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.hashes, never()).putIfAbsent(anyString(), anyString(), anyString());
	}

	@Test
	void returnsNotApplicableForADuplicateWithoutQueuingAgain() {
		Fixture fixture = fixture(ActionTypeEnum.PAYMENT);
		when(fixture.hashes.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(false);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isTrue();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.lists, never()).rightPush(anyString(), anyString());
	}

	@Test
	void returnsForwardedWhenTheLegacyRatioIsGreaterThanOne() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.helper.getRouteRatio("link-id", "media-id")).thenReturn(1.5);
		when(fixture.helper.getRouteOriginalCounts("link-id", "media-id")).thenReturn(new int[11]);
		when(fixture.helper.getRouteDeliveredCounts("link-id", "media-id")).thenReturn(new int[11]);

		var result = fixture.service.handleCallback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.FORWARDED);
	}

	@Test
	void handlesFirstOrderWithoutOverflow() {
		Fixture fixture = fixture(ActionTypeEnum.FIRST_ORDER);
		fixture.request.setPartnerActionType("firstorder");
		when(fixture.helper.getRouteRatio("link-id", "media-id")).thenReturn(1.0);
		when(fixture.helper.getRouteOriginalCounts("link-id", "media-id")).thenReturn(new int[19]);
		when(fixture.helper.getRouteDeliveredCounts("link-id", "media-id")).thenReturn(new int[19]);

		assertThat(fixture.service.handleCallback(fixture.request).decision()).isEqualTo(CallbackDecision.FORWARDED);
		var payload = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(fixture.lists).rightPush(anyString(), payload.capture());
		LogData logData = JSON.parseObject(payload.getValue(), LogData.class);
		assertThat(logData.getFirstOrder()).isEqualTo(1);
		assertThat(logData.getStandardActionType()).isEqualTo(19);
		assertThat(logData.getPartnerActionType()).isEqualTo("firstorder");
		verify(fixture.helper).setRouteOriginalCounts(anyString(), anyString(), any(int[].class));
	}

	@Test
	void queuesPurchaseAmountFidelity() {
		Fixture fixture = fixture(ActionTypeEnum.PAYMENT);
		fixture.request.setPartnerActionType("purchase");
		fixture.request.setCurrencyAmount(new BigDecimal("123.450000"));
		when(fixture.helper.getRouteRatio("link-id", "media-id")).thenReturn(1.0);
		when(fixture.helper.getRouteOriginalCounts("link-id", "media-id")).thenReturn(new int[19]);
		when(fixture.helper.getRouteDeliveredCounts("link-id", "media-id")).thenReturn(new int[19]);

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
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		RedisHelper helper = mock(RedisHelper.class);
		when(redis.opsForHash()).thenReturn(hashes);
		when(redis.opsForList()).thenReturn(lists);
		when(hashes.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(true);
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-1")
			.build();
		CallbackReq request = CallbackReq.builder().feedback(feedback).actionType(actionType).actionTime(1L).build();
		return new Fixture(new CallbackServiceImpl(redis, 1, helper), helper, hashes, lists, request);
	}

	private record Fixture(CallbackServiceImpl service, RedisHelper helper,
			HashOperations<String, Object, Object> hashes, ListOperations<String, String> lists, CallbackReq request) {
	}

}
