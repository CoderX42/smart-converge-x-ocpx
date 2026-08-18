package com.yzytech.scx.ocpx.feedback.service;

import com.yzytech.scx.ocpx.callback.service.TestCallbackCollectorService;
import com.yzytech.scx.ocpx.fact.service.OcpxEventFactService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceImplTest {

	@Test
	void duplicateDoesNotQueueOrForwardAgain() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		when(redis.opsForList()).thenReturn(lists);
		OcpxEventFactService eventFactService = mock(OcpxEventFactService.class);
		when(eventFactService.recordFeedback(org.mockito.ArgumentMatchers.any()))
			.thenReturn(new OcpxEventFactService.FeedbackFactResult(false, false, "DUPLICATE", BigDecimal.ZERO));
		TestCallbackCollectorService collectorService = mock(TestCallbackCollectorService.class);
		FeedbackServiceImpl service = new FeedbackServiceImpl(redis, 1, eventFactService, collectorService);
		FeedbackReq request = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.event(EventTypeEnum.CLICK)
			.reqId("request-1")
			.build();

		var result = service.handleFeedback(request);

		assertThat(result.firstReport()).isFalse();
		assertThat(request.getClkSmt()).isZero();
		assertThat(request.getUniClkSmt()).isZero();
		verify(lists, never()).rightPush(anyString(), anyString());
	}

}
