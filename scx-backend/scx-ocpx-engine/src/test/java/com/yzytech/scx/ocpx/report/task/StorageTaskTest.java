package com.yzytech.scx.ocpx.report.task;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageTaskTest {

	@Test
	@SuppressWarnings("unchecked")
	void readsZeroThroughOneThousandWritesAndThenTrims() {
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
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 1000)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		StorageTask task = new StorageTask(redis, reportService, 1);

		task.consumeFeedback();

		var ordered = inOrder(reportService, lists);
		ordered.verify(reportService).addFeedback(anyList());
		ordered.verify(lists).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).delete("rtb{consumeFeedback0}");
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
		when(lists.range(RedisConstants.DATA_REPORT + "0", 0, 1000)).thenReturn(List.of("{\"reqId\":\"r1\"}"));
		doThrow(new IllegalStateException("db down")).when(reportService).addFeedback(anyList());

		new StorageTask(redis, reportService, 1).consumeFeedback();

		verify(lists, never()).trim(RedisConstants.DATA_REPORT + "0", 1, -1);
		verify(redis).delete("rtb{consumeFeedback0}");
	}

}
