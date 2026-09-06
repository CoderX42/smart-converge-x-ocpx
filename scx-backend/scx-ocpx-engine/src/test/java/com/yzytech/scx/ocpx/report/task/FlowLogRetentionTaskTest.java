package com.yzytech.scx.ocpx.report.task;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowLogRetentionTaskTest {

	@Test
	void cleansSevenDayDetailsAndNinetyDayMetadataInBatches() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{flowLogRetention}"), anyString(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(true);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(3);
		when(jdbc.update(anyString())).thenReturn(1000, 2, 0, 0, 0, 0);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(java.util.List.of());
		FlowLogRetentionTask task = new FlowLogRetentionTask(redis, jdbc);
		ReflectionTestUtils.setField(task, "batchPauseMs", 0L);

		task.clean();

		var sql = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbc, org.mockito.Mockito.times(5)).update(sql.capture());
		assertThat(sql.getAllValues()).anySatisfy(
				value -> assertThat(value).contains("scx_ocpx_flow_log_detail", "expires_at<NOW(3)", "LIMIT 1000"));
		assertThat(sql.getAllValues())
			.anySatisfy(value -> assertThat(value).contains("scx_ocpx_log_feedback", "INTERVAL 7 DAY", "LIMIT 1000"));
		assertThat(sql.getAllValues())
			.anySatisfy(value -> assertThat(value).contains("scx_ocpx_log_callback", "INTERVAL 7 DAY", "LIMIT 1000"));
		assertThat(sql.getAllValues()).anySatisfy(
				value -> assertThat(value).contains("DELETE FROM scx_ocpx_flow_log", "INTERVAL 90 DAY", "LIMIT 1000"));
		var querySql = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbc).query(querySql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), org.mockito.ArgumentMatchers.any());
		assertThat(querySql.getValue()).contains("response_preview_present=1", "response_preview IS NOT NULL",
				"FORCE INDEX (idx_ocpx_flow_log_preview_retention)",
				"ORDER BY event_time,id", "LIMIT ?");
		verify(redis).execute(any(), eq(java.util.List.of("rtb{flowLogRetention}")), anyString());
	}

	@Test
	void anotherInstanceDoesNothingWhileTheRedisMutexIsHeld() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{flowLogRetention}"), anyString(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(false);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(3);

		new FlowLogRetentionTask(redis, jdbc).clean();

		verify(jdbc, never()).update(anyString());
		verify(redis, never()).execute(any(), eq(java.util.List.of("rtb{flowLogRetention}")), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void capsEachRunAtConfiguredBatchesAcrossDeletionTargets() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{flowLogRetention}"), anyString(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(true);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(3);
		when(jdbc.update(anyString())).thenReturn(1000);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
			.thenReturn(java.util.List.of());
		FlowLogRetentionTask task = new FlowLogRetentionTask(redis, jdbc);
		ReflectionTestUtils.setField(task, "batchPauseMs", 0L);

		task.clean();

		verify(jdbc, org.mockito.Mockito.times(8)).update(anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void releasesTheMutexWhenADeletionFails() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{flowLogRetention}"), anyString(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(true);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(3);
		when(jdbc.update(anyString())).thenThrow(new IllegalStateException("db down"));

		new FlowLogRetentionTask(redis, jdbc).clean();

		verify(redis).execute(any(), eq(java.util.List.of("rtb{flowLogRetention}")), anyString());
	}

	@Test
	void skipsPreviewScanUntilTheSelectiveIndexExists() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(eq("rtb{flowLogRetention}"), anyString(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(true);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

		new FlowLogRetentionTask(redis, jdbc).clean();

		verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
				any(Object[].class));
	}

}
