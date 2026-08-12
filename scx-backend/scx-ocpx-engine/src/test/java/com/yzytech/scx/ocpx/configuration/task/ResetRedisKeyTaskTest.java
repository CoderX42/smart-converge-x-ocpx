package com.yzytech.scx.ocpx.configuration.task;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.configuration.bootstrap.SystemMemory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
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

		new ResetRedisKeyTask(redis, mock(SystemMemory.class)).clearKey();

		verify(redis, times(100)).unlink(org.mockito.ArgumentMatchers.startsWith(RedisConstants.REPEAT_REPORT));
		verify(redis).unlink(RedisConstants.REPEAT_REPORT + "0");
		verify(redis).unlink(RedisConstants.REPEAT_REPORT + "99");
	}

	@Test
	void reloadsMemoryAfterTheSharedRefreshMarkerAndDeletesIt() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		SystemMemory memory = mock(SystemMemory.class);
		when(redis.hasKey(RedisConstants.DATA_REFRESH)).thenReturn(true);

		new ResetRedisKeyTask(redis, memory).refreshMemoryData();

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

		new ResetRedisKeyTask(redis, mock(SystemMemory.class)).resetRouteCounters();

		verify(redis).delete(original);
		verify(redis).delete(delivered);
	}

}
