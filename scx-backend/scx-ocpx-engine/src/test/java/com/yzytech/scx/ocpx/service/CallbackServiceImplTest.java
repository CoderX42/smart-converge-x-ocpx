package com.yzytech.scx.ocpx.service;

import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.service.helper.RedisHelper;
import com.yzytech.scx.ocpx.service.impl.CallbackServiceImpl;
import com.yzytech.scx.ocpx.service.result.CallbackDecision;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackServiceImplTest {

	@Test
	void returnsDeductedWhenLegacyRatioDropsTheCallback() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.helper.getAdvertisingRadio("link-id", "media-id")).thenReturn(0.5);
		when(fixture.helper.getAdvertisingOriginal("link-id", "media-id")).thenReturn(new int[11]);
		when(fixture.helper.getAdvertisingFixed("link-id", "media-id")).thenReturn(new int[11]);

		var result = fixture.service.handleFeedback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.DEDUCTED);
	}

	@Test
	void returnsNotApplicableForAnEmptyAction() {
		Fixture fixture = fixture(null);

		var result = fixture.service.handleFeedback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.hashes, never()).putIfAbsent(anyString(), anyString(), anyString());
	}

	@Test
	void returnsNotApplicableForADuplicateWithoutQueuingAgain() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.hashes.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(false);

		var result = fixture.service.handleFeedback(fixture.request);

		assertThat(result.duplicate()).isTrue();
		assertThat(result.decision()).isEqualTo(CallbackDecision.NOT_APPLICABLE);
		verify(fixture.lists, never()).rightPush(anyString(), anyString());
	}

	@Test
	void returnsForwardedWhenTheLegacyRatioIsGreaterThanOne() {
		Fixture fixture = fixture(ActionTypeEnum.ACTIVATE);
		when(fixture.helper.getAdvertisingRadio("link-id", "media-id")).thenReturn(1.5);
		when(fixture.helper.getAdvertisingOriginal("link-id", "media-id")).thenReturn(new int[11]);
		when(fixture.helper.getAdvertisingFixed("link-id", "media-id")).thenReturn(new int[11]);

		var result = fixture.service.handleFeedback(fixture.request);

		assertThat(result.duplicate()).isFalse();
		assertThat(result.decision()).isEqualTo(CallbackDecision.FORWARDED);
	}

	@Test
	void preservesTheElevenSlotArrayOverflow() {
		Fixture fixture = fixture(ActionTypeEnum.EVOKE_C);
		when(fixture.helper.getAdvertisingRadio("link-id", "media-id")).thenReturn(1.0);
		when(fixture.helper.getAdvertisingOriginal("link-id", "media-id")).thenReturn(new int[11]);
		when(fixture.helper.getAdvertisingFixed("link-id", "media-id")).thenReturn(new int[11]);

		assertThatThrownBy(() -> fixture.service.handleFeedback(fixture.request))
			.isInstanceOf(ArrayIndexOutOfBoundsException.class);
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
			.chid("media-id")
			.mid("task-id")
			.dspid("advertiser-id")
			.linkid("link-id")
			.reqId("request-1")
			.build();
		CallbackReq request = CallbackReq.builder().feedback(feedback).actionType(actionType).actionTime(1L).build();
		return new Fixture(new CallbackServiceImpl(redis, 1, helper), helper, hashes, lists, request);
	}

	private record Fixture(CallbackServiceImpl service, RedisHelper helper,
			HashOperations<String, Object, Object> hashes, ListOperations<String, String> lists, CallbackReq request) {
	}

}
