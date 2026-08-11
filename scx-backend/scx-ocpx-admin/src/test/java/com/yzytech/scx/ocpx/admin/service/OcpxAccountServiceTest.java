package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxAccountServiceTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxAccountService service = new OcpxAccountService(jdbcTemplate, accessScope);

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(accessScope.constrainAdvertiser("advertiser-1")).thenReturn("advertiser-1");
		when(jdbcTemplate.query(contains("SELECT balance"), any(RowMapper.class), eq("advertiser-1")))
			.thenReturn(List.of(new BigDecimal("20.000000")));
	}

	@Test
	void rechargePostsBalanceAndImmutableLedgerTogether() {
		when(jdbcTemplate.query(contains("WHERE charge_event_id"), any(RowMapper.class),
				eq("RECHARGE:advertiser-1:request_20260807_01")))
			.thenReturn(List.of());

		CatalogViews.AccountMutation result = service.recharge("advertiser-1", "request_20260807_01",
				new BigDecimal("100.00"), "operator");

		assertThat(result.balance()).isEqualByComparingTo("120.000000");
		assertThat(result.duplicate()).isFalse();
		ArgumentCaptor<Object[]> balanceUpdate = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("UPDATE scx_ocpx_advertiser"), balanceUpdate.capture());
		assertThat(balanceUpdate.getValue()[0]).isEqualTo(new BigDecimal("120.000000"));
		verify(jdbcTemplate).update(contains("INSERT INTO scx_ocpx_billing_entry"), any(Object[].class));
	}

	@Test
	void rechargeReturnsExistingLedgerForSameRequestId() {
		CatalogViews.AccountMutation existing = new CatalogViews.AccountMutation("advertiser-1", "ledger-1",
				new BigDecimal("120.000000"), true);
		when(jdbcTemplate.query(contains("WHERE charge_event_id"), any(RowMapper.class),
				eq("RECHARGE:advertiser-1:request_20260807_01")))
			.thenReturn(List.of(existing));

		CatalogViews.AccountMutation result = service.recharge("advertiser-1", "request_20260807_01",
				new BigDecimal("100.00"), "operator");

		assertThat(result).isEqualTo(existing);
	}

}
