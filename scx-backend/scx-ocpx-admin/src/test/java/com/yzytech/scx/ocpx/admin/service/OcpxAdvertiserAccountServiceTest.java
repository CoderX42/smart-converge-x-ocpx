package com.yzytech.scx.ocpx.admin.service;

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
		service = new OcpxAdvertiserAccountService(jdbcTemplate, advertiserScope);
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

	private int indexContaining(List<String> values, String expected) {
		for (int index = 0; index < values.size(); index++) {
			if (values.get(index).contains(expected)) {
				return index;
			}
		}
		throw new AssertionError("SQL not found: " + expected);
	}

}
