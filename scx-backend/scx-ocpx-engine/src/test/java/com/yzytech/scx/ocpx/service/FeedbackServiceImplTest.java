package com.yzytech.scx.ocpx.service;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.RedisConstants;
import com.yzytech.scx.ocpx.constant.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.service.impl.FeedbackServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceImplTest {

	@Test
	void reportsDedupDecisionButStillQueuesTheEvent() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		when(redis.opsForList()).thenReturn(lists);
		when(hashes.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(false);
		FeedbackServiceImpl service = new FeedbackServiceImpl(redis, 1);
		FeedbackReq request = FeedbackReq.builder()
			.chid("media-id")
			.mid("task-id")
			.dspid("advertiser-id")
			.linkid("link-id")
			.event(EventTypeEnum.CLICK)
			.reqId("request-1")
			.build();

		var result = service.handleFeedback(request);

		assertThat(result.firstReport()).isFalse();
		assertThat(request.getClkSmt()).isEqualTo(1);
		assertThat(request.getUniClkSmt()).isZero();
		verify(lists).rightPush(org.mockito.ArgumentMatchers.eq(RedisConstants.DATA_REPORT + "0"), anyString());
	}

}
