package com.yzytech.scx.ocpx.configuration.task;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.configuration.bootstrap.SystemMemory;
import com.yzytech.scx.ocpx.report.mapper.FeedbackMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResetRedisKeyTaskTest {

	@Test
	@SuppressWarnings("unchecked")
	void clearsAllOneHundredFeedbackDedupBucketsEachDay() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent("rtb{clearKey}", "1", 15, TimeUnit.MINUTES)).thenReturn(true);

		new ResetRedisKeyTask(redis, mock(SystemMemory.class), mock(FeedbackMapper.class)).clearKey();

		verify(redis, times(100)).unlink(org.mockito.ArgumentMatchers.startsWith(RedisConstants.REPEAT_REPORT));
		verify(redis).unlink(RedisConstants.REPEAT_REPORT + "0");
		verify(redis).unlink(RedisConstants.REPEAT_REPORT + "99");
	}

	@Test
	void reloadsMemoryAfterTheSharedRefreshMarkerAndDeletesIt() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		SystemMemory memory = mock(SystemMemory.class);
		when(redis.hasKey(RedisConstants.DATA_REFRESH)).thenReturn(true);

		new ResetRedisKeyTask(redis, memory, mock(FeedbackMapper.class)).refreshMemoryData();

		verify(memory).init();
		verify(redis).unlink(RedisConstants.DATA_REFRESH);
	}

	@Test
	void clearsTheRouteDeductionCountersAtMidnight() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		Set<String> original = Set.of(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX + "link:media");
		Set<String> delivered = Set.of(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX + "link:media");
		when(redis.keys(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX + "*")).thenReturn(original);
		when(redis.keys(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX + "*")).thenReturn(delivered);

		new ResetRedisKeyTask(redis, mock(SystemMemory.class), mock(FeedbackMapper.class)).resetRouteCounters();

		verify(redis).delete(original);
		verify(redis).delete(delivered);
	}

	@Test
	@SuppressWarnings("unchecked")
	void deletesAtMostOneBoundedBatchBeforeCurrentDay() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent("rtb{deleteFeedbackDay}", "1", 15, TimeUnit.MINUTES)).thenReturn(true);

		new ResetRedisKeyTask(redis, mock(SystemMemory.class), feedbackMapper).deleteFeedbackDay();

		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		verify(feedbackMapper).deleteFeedbackDayBefore(today + "00", 1000);
	}

	@Test
	void capsFeedbackDayCleanupBatches() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{deleteFeedbackDay}"), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
				.thenReturn(true);
		when(feedbackMapper.deleteFeedbackDayBefore(anyString(), eq(10))).thenReturn(10, 10, 10);

		new ResetRedisKeyTask(redis, mock(SystemMemory.class), feedbackMapper, 10, 2, 0L).deleteFeedbackDay();

		verify(feedbackMapper, times(2)).deleteFeedbackDayBefore(anyString(), eq(10));
	}

	@Test
	@SuppressWarnings("unchecked")
	void skipsFeedbackDayCleanupWhenAnotherInstanceHoldsTheLock() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{deleteFeedbackDay}"), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
			.thenReturn(false);

		new ResetRedisKeyTask(redis, mock(SystemMemory.class), feedbackMapper).deleteFeedbackDay();

		verify(feedbackMapper, never()).deleteFeedbackDay(anyInt());
	}

}
