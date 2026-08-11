package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.admin.config.OcpxRuntimeCenterProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

class OcpxRuntimeReplayWorkerTest {

	@Test
	void literalDefaultIsDisabledAndDoesNotClaimOrInvokeProcessor() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		TransactionTemplate transaction = mock(TransactionTemplate.class);
		OcpxRuntimeReplayProcessor processor = mock(OcpxRuntimeReplayProcessor.class);
		OcpxRuntimeCenterProperties properties = new OcpxRuntimeCenterProperties();

		int claimed = new OcpxRuntimeReplayWorker(jdbc, transaction, processor, properties).drainOnce();

		assertThat(claimed).isZero();
		assertThat(properties.isReplayWorkerEnabled()).isFalse();
		verifyNoInteractions(jdbc, transaction, processor);
	}

	@Test
	void enabledFlagStillFailsClosedWithoutAnExplicitProcessor() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		TransactionTemplate transaction = mock(TransactionTemplate.class);
		OcpxRuntimeReplayProcessor processor = mock(OcpxRuntimeReplayProcessor.class);
		OcpxRuntimeCenterProperties properties = new OcpxRuntimeCenterProperties();
		properties.setReplayWorkerEnabled(true);
		when(processor.available()).thenReturn(false);

		int claimed = new OcpxRuntimeReplayWorker(jdbc, transaction, processor, properties).drainOnce();

		assertThat(claimed).isZero();
		verify(processor).available();
		verifyNoInteractions(jdbc, transaction);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void fakeProcessorSuccessAppendsResultAndContributionWithoutRewritingFailure() throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		TransactionTemplate transaction = mock(TransactionTemplate.class);
		OcpxRuntimeReplayProcessor processor = mock(OcpxRuntimeReplayProcessor.class);
		OcpxRuntimeCenterProperties properties = new OcpxRuntimeCenterProperties();
		properties.setReplayWorkerEnabled(true);
		properties.setMaxReplayBatch(1);

		when(processor.available()).thenReturn(true);
		when(processor.process(any())).thenReturn(OcpxRuntimeReplayProcessor.Result.succeeded(1));
		when(transaction.execute(any(TransactionCallback.class)))
			.thenAnswer(invocation -> ((TransactionCallback) invocation.getArgument(0))
				.doInTransaction(mock(TransactionStatus.class)));
		doAnswer(invocation -> {
			((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class));
			return null;
		}).when(transaction).executeWithoutResult(any());
		when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(claimRow(), 0)));
		when(jdbc.queryForObject(contains("SELECT attempt_count"), eq(Integer.class), any(Object[].class)))
			.thenReturn(0);
		when(jdbc.queryForObject(contains("scx_ocpx_runtime_fact WHERE idempotency_key_hash"), eq(String.class),
				any(Object[].class)))
			.thenReturn("result-fact-1");
		when(jdbc.queryForObject(contains("scx_ocpx_hourly_fact_contribution"), eq(String.class), any(Object[].class)))
			.thenReturn("contribution-1");
		when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

		int claimed = new OcpxRuntimeReplayWorker(jdbc, transaction, processor, properties).drainOnce();

		assertThat(claimed).isEqualTo(1);
		verify(processor).process(any(OcpxRuntimeReplayProcessor.Context.class));
		verify(jdbc).update(contains("INSERT IGNORE INTO scx_ocpx_runtime_fact"), any(Object[].class));
		verify(jdbc).update(contains("INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution"), any(Object[].class));
		verify(jdbc, never()).update(contains("UPDATE scx_ocpx_runtime_failure"), any(Object[].class));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, atLeastOnce()).update(sql.capture(), arguments.capture());
		for (int index = 0; index < sql.getAllValues().size(); index++) {
			String statement = sql.getAllValues().get(index);
			if (statement.contains("INSERT IGNORE INTO scx_ocpx_runtime_fact")
					|| statement.contains("INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution")) {
				long placeholders = statement.chars().filter(character -> character == '?').count();
				assertThat(arguments.getAllValues().get(index)).hasSize((int) placeholders);
			}
		}
	}

	private ResultSet claimRow() throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString(1)).thenReturn("command-1");
		when(resultSet.getString(2)).thenReturn("failure-1");
		when(resultSet.getString(3)).thenReturn("route-version-1");
		when(resultSet.getLong(4)).thenReturn(3L);
		when(resultSet.getObject(5)).thenReturn(2);
		when(resultSet.getInt(5)).thenReturn(2);
		when(resultSet.getInt(6)).thenReturn(0);
		when(resultSet.getInt(7)).thenReturn(5);
		when(resultSet.getString(8)).thenReturn("tenant-1");
		when(resultSet.getString(9)).thenReturn("advertiser-1");
		when(resultSet.getString(10)).thenReturn("product-1");
		when(resultSet.getString(11)).thenReturn("app-link-1");
		when(resultSet.getString(12)).thenReturn("task-1");
		when(resultSet.getString(13)).thenReturn("channel-1");
		when(resultSet.getString(14)).thenReturn("media-account-1");
		when(resultSet.getString(15)).thenReturn("operator-1");
		when(resultSet.getString(16)).thenReturn("route-1");
		LocalDateTime now = LocalDateTime.parse("2026-08-10T08:00:00");
		when(resultSet.getTimestamp(17)).thenReturn(Timestamp.valueOf(now));
		when(resultSet.getTimestamp(18)).thenReturn(Timestamp.valueOf(now.plusMinutes(1)));
		return resultSet;
	}

}
