package com.yzytech.scx.ocpx.runtime.billing;

import com.yzytech.scx.ocpx.contract.BillingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcBillingPortTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final JdbcBillingPort billingPort = new JdbcBillingPort(jdbcTemplate);

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(jdbcTemplate.query(contains("SELECT balance"), any(RowMapper.class), eq("advertiser-1")))
			.thenReturn(List.of(new BigDecimal("10.000000")));
		when(jdbcTemplate.query(contains("SELECT status"), any(RowMapper.class), eq("click-1"))).thenReturn(List.of());
	}

	@Test
	void postsCpcChargeAndReturnsBalanceAfter() {
		when(jdbcTemplate.update(contains("UPDATE scx_adx_advertiser"), any(Object[].class))).thenReturn(1);

		BillingPort.BillingResult result = billingPort.charge("click-1", "advertiser-1", new BigDecimal("1.250000"),
				"CNY", "CPC");

		assertThat(result.status()).isEqualTo("POSTED");
		assertThat(result.balanceAfter()).isEqualByComparingTo("8.750000");
		ArgumentCaptor<Object[]> entry = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("INSERT INTO scx_ocpx_billing_entry"), entry.capture());
		assertThat(entry.getValue()[6]).isEqualTo("POSTED");
	}

	@Test
	void recordsRejectionWithoutMutatingBalance() {
		BillingPort.BillingResult result = billingPort.charge("click-1", "advertiser-1", new BigDecimal("12.000000"),
				"CNY", "CPC");

		assertThat(result.status()).isEqualTo("REJECTED");
		assertThat(result.permitsDelivery()).isFalse();
		verify(jdbcTemplate, never()).update(contains("UPDATE scx_adx_advertiser"), any(Object[].class));
		ArgumentCaptor<Object[]> entry = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("INSERT INTO scx_ocpx_billing_entry"), entry.capture());
		assertThat(entry.getValue()[6]).isEqualTo("REJECTED");
	}

	@Test
	void auditOnlyCreatesLedgerWithoutMutatingBalance() {
		BillingPort.BillingResult result = billingPort.charge("click-1", "advertiser-1", new BigDecimal("1.250000"),
				"CNY", "AUDIT_ONLY");

		assertThat(result.status()).isEqualTo("RECORDED");
		assertThat(result.balanceAfter()).isEqualByComparingTo("10.000000");
		verify(jdbcTemplate, never()).update(contains("UPDATE scx_adx_advertiser"), any(Object[].class));
	}

}
