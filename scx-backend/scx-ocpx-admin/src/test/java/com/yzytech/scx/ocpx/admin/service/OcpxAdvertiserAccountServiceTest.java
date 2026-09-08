package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserAccountSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxAdvertiserAccountServiceTest {

	private JdbcTemplate jdbcTemplate;

	private AdvertiserScope advertiserScope;

	private OcpxAdvertiserAccountService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxAdvertiserAccountService(
				XmlMapperTestSupport.mapper(OcpxAdvertiserAccountSqlMapper.class, jdbcTemplate), advertiserScope);
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void rechargeLocksTheAccountAndPostsAnExactSixDecimalImmutableEntry() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FOR UPDATE")
					? List.of(new BigDecimal("1.000000")) : List.of());

		var result = service.recharge("adv-1",
				new AdvertiserRequests.Recharge(" request-001 ", new BigDecimal("100.123456"), " 首充 "));

		assertThat(result.getAmount()).isEqualByComparingTo("100.123456");
		assertThat(result.getBalanceAfter()).isEqualByComparingTo("101.123456");
		verify(advertiserScope).requireSystemAdministrator();
		ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, atLeastOnce()).query(querySql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(querySql.getAllValues()).anyMatch(value -> value.contains("FOR UPDATE"));
		verify(jdbcTemplate, atLeastOnce()).update(updateSql.capture(), updateArgs.capture());
		int accountUpdate = indexContaining(updateSql.getAllValues(), "UPDATE scx_ocpx_advertiser_account");
		assertThat((BigDecimal) updateArgs.getAllValues().get(accountUpdate)[0]).isEqualByComparingTo("101.123456");
		int ledgerInsert = indexContaining(updateSql.getAllValues(), "INSERT INTO scx_ocpx_billing_entry");
		Object[] ledger = updateArgs.getAllValues().get(ledgerInsert);
		assertThat(ledger).contains("adv-1", new BigDecimal("100.123456"), new BigDecimal("1.000000"),
				new BigDecimal("101.123456"), "RECHARGE:adv-1:request-001", "首充");
		assertThat(updateSql.getAllValues()).noneMatch(value -> value.contains("UPDATE scx_ocpx_billing_entry"));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void repeatingTheSameRequestReturnsTheOriginalEntryWithoutChangingBalance() throws Exception {
		LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 15, 10, 30);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("FOR UPDATE")) {
				return List.of(new BigDecimal("101.000000"));
			}
			RowMapper mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getString("id")).thenReturn("entry-original");
			when(resultSet.getString("advertiser_id")).thenReturn("adv-1");
			when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("100.000000"));
			when(resultSet.getBigDecimal("balance_after")).thenReturn(new BigDecimal("101.000000"));
			when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(occurredAt));
			return List.of(mapper.mapRow(resultSet, 0));
		});

		var result = service.recharge("adv-1",
				new AdvertiserRequests.Recharge("request-001", new BigDecimal("100"), null));

		assertThat(result.getEntryId()).isEqualTo("entry-original");
		assertThat(result.getOccurredAt()).isEqualTo(occurredAt);
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void repeatingARequestIdWithAnotherAmountFailsClosed() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("FOR UPDATE")) {
				return List.of(new BigDecimal("101.000000"));
			}
			RowMapper mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getString("id")).thenReturn("entry-original");
			when(resultSet.getString("advertiser_id")).thenReturn("adv-1");
			when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("100.000000"));
			when(resultSet.getBigDecimal("balance_after")).thenReturn(new BigDecimal("101.000000"));
			when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
			return List.of(mapper.mapRow(resultSet, 0));
		});

		assertThatThrownBy(() -> service.recharge("adv-1",
				new AdvertiserRequests.Recharge("request-001", new BigDecimal("99"), null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("requestId");
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void rechargeMethodIsTransactionalForConcurrentAccountUpdates() throws Exception {
		assertThat(OcpxAdvertiserAccountService.class
			.getMethod("recharge", String.class, AdvertiserRequests.Recharge.class)
			.getAnnotation(Transactional.class)).isNotNull();
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void deductLocksTheAccountAndPostsAPositiveManualDebit() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FOR UPDATE")
					? List.of(new BigDecimal("100.000000")) : List.of());

		var result = service.deduct("adv-1",
				new AdvertiserRequests.Deduction(" deduct-001 ", new BigDecimal("30.250000"), " 线下退款 "));

		assertThat(result.getAmount()).isEqualByComparingTo("30.250000");
		assertThat(result.getBalanceAfter()).isEqualByComparingTo("69.750000");
		verify(advertiserScope).requireSystemAdministrator();
		ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, atLeastOnce()).update(updateSql.capture(), updateArgs.capture());
		int accountUpdate = indexContaining(updateSql.getAllValues(), "UPDATE scx_ocpx_advertiser_account");
		assertThat((BigDecimal) updateArgs.getAllValues().get(accountUpdate)[0]).isEqualByComparingTo("69.750000");
		int ledgerInsert = indexContaining(updateSql.getAllValues(), "INSERT INTO scx_ocpx_billing_entry");
		assertThat(updateSql.getAllValues().get(ledgerInsert)).contains("'MANUAL_DEBIT'");
		assertThat(updateArgs.getAllValues().get(ledgerInsert)).contains("adv-1", new BigDecimal("30.250000"),
				new BigDecimal("100.000000"), new BigDecimal("69.750000"), "MANUAL_DEBIT:adv-1:deduct-001", "线下退款");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void deductRejectsAnOverdrawWithoutChangingTheAccountOrLedger() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FOR UPDATE")
					? List.of(new BigDecimal("20.000000")) : List.of());

		assertThatThrownBy(() -> service.deduct("adv-1",
				new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("20.000001"), "余额调账")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不能超过当前可用余额");
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void repeatingTheSameDeductionReturnsTheOriginalEntryBeforeCheckingCurrentBalance() throws Exception {
		LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 17, 16, 30);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("FOR UPDATE")) {
				return List.of(BigDecimal.ZERO.setScale(6));
			}
			RowMapper mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getString("id")).thenReturn("deduct-original");
			when(resultSet.getString("advertiser_id")).thenReturn("adv-1");
			when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("30.000000"));
			when(resultSet.getBigDecimal("balance_after")).thenReturn(BigDecimal.ZERO.setScale(6));
			when(resultSet.getString("remark")).thenReturn("余额调账");
			when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(occurredAt));
			return List.of(mapper.mapRow(resultSet, 0));
		});

		var result = service.deduct("adv-1",
				new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("30"), "余额调账"));

		assertThat(result.getEntryId()).isEqualTo("deduct-original");
		assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void repeatingADeductionRequestWithAnotherReasonFailsClosed() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("FOR UPDATE")) {
				return List.of(new BigDecimal("70.000000"));
			}
			RowMapper mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getString("id")).thenReturn("deduct-original");
			when(resultSet.getString("advertiser_id")).thenReturn("adv-1");
			when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("30.000000"));
			when(resultSet.getBigDecimal("balance_after")).thenReturn(new BigDecimal("70.000000"));
			when(resultSet.getString("remark")).thenReturn("原始原因");
			when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
			return List.of(mapper.mapRow(resultSet, 0));
		});

		assertThatThrownBy(() -> service.deduct("adv-1",
				new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("30"), "新原因")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("原因");
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void deductMethodIsTransactionalForConcurrentAccountUpdates() throws Exception {
		assertThat(
				OcpxAdvertiserAccountService.class.getMethod("deduct", String.class, AdvertiserRequests.Deduction.class)
					.getAnnotation(Transactional.class))
			.isNotNull();
	}

	private int indexContaining(List<String> values, String expected) {
		for (int index = 0; index < values.size(); index++) {
			if (values.get(index).contains(expected)) {
				return index;
			}
		}
		throw new AssertionError("SQL not found: " + expected);
	}

}
