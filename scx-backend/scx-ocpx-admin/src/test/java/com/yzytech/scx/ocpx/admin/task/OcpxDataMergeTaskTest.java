package com.yzytech.scx.ocpx.admin.task;

import com.yzytech.scx.ocpx.admin.service.OcpxReportMergeService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxDataMergeTaskTest {

	@Test
	@SuppressWarnings("unchecked")
	void runsMinuteAndHourAggregationWithTheLegacyLocks() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		OcpxReportMergeService reportService = mock(OcpxReportMergeService.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent("admin{groupReportMinute}", "1", 50, TimeUnit.SECONDS)).thenReturn(true);
		when(values.setIfAbsent("admin{groupReport}", "1", 10, TimeUnit.MINUTES)).thenReturn(true);
		OcpxDataMergeTask task = new OcpxDataMergeTask(redis, reportService, 1);

		task.groupReportMinute();
		task.groupReport();

		verify(reportService).groupReportMinute(
				argThat(value -> value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")),
				org.mockito.ArgumentMatchers.eq(0));
		verify(reportService).groupReport(argThat(value -> value.matches("\\d{10}")),
				org.mockito.ArgumentMatchers.eq(0));
		verify(redis).unlink("admin{groupReportMinute}");
		verify(redis).unlink("admin{groupReport}");
	}

}
