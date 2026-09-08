package com.yzytech.scx.ocpx.report.task;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.report.service.ReportService;
import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageTaskTest {

	@Test
	@SuppressWarnings("unchecked")
	void readsConfiguredBatchAndThenTrims() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenReturn(1L, 0L);
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 499)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		StorageTask task = new StorageTask(redis, reportService, 1);

		task.consumeFeedback();

		var ordered = inOrder(reportService, lists);
		ordered.verify(reportService).addFeedback(anyList());
		ordered.verify(lists).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void doesNotTrimWhenTheDatabaseWriteFails() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenReturn(1L, 0L);
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 499)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		doThrow(new IllegalStateException("db down")).when(reportService).addFeedback(anyList());

		new StorageTask(redis, reportService, 1, 500, 0, 0L).consumeFeedback();

		verify(reportService).addFeedback(anyList());
		verify(lists, never()).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void retriesDatabaseFailureOnlyWithinConfiguredLimit() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenReturn(1L);
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 499)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		doThrow(new IllegalStateException("db down")).when(reportService).addFeedback(anyList());

		new StorageTask(redis, reportService, 1, 500, 2, 0L).consumeFeedback();

		verify(reportService, org.mockito.Mockito.times(3)).addFeedback(anyList());
		verify(lists, never()).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void exitsTheCurrentRoundWhenRedisTimesOut() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenThrow(new RedisCommandTimeoutException("redis timeout"));

		new StorageTask(redis, reportService, 1, 500, 3, 0L).consumeFeedback();

		verify(lists, never()).range(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong());
		verify(reportService, never()).addFeedback(anyList());
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void exitsWithoutAStaleLockWhenLockAcquisitionTimesOut() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenThrow(new RedisCommandTimeoutException("redis timeout"));

		new StorageTask(redis, reportService, 1, 500, 3, 0L).consumeFeedback();

		verify(lists, never()).size(org.mockito.ArgumentMatchers.anyString());
		verify(reportService, never()).addFeedback(anyList());
		verify(redis, never()).delete(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void trimsACompletelySkippedBatchInsteadOfSpinning() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenReturn(1L, 0L);
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 499)).thenReturn(List.of("", "{\\u4e2d\\u6587}"));

		new StorageTask(redis, reportService, 1, 500, 0, 0L).consumeFeedback();

		verify(lists).trim(RedisConstants.DATA_REPORT + "0", 2, -1);
		verify(reportService, never()).addFeedback(anyList());
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void stopsDrainingAfterTheConfiguredRunBudget() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		ListOperations<String, String> lists = mock(ListOperations.class);
		ReportService reportService = mock(ReportService.class);
		AtomicLong now = new AtomicLong();
		when(redis.opsForValue()).thenReturn(values);
		when(redis.opsForList()).thenReturn(lists);
		when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(true);
		when(lists.size(RedisConstants.DATA_REPORT + "0")).thenReturn(1L, 1L);
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 499)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		org.mockito.Mockito.doAnswer(invocation -> {
			now.addAndGet(TimeUnit.MILLISECONDS.toNanos(31_000));
			return null;
		}).when(reportService).addFeedback(anyList());

		new StorageTask(redis, reportService, 1, 500, 0, 0L, 20, 30_000L, 0L, now::get).consumeFeedback();

		verify(reportService).addFeedback(anyList());
		verify(lists).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).execute(any(), eq(List.of("rtb{consumeFeedback0}")), anyString());
	}

}
