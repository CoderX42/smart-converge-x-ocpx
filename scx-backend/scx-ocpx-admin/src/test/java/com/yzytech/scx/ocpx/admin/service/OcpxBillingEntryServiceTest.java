package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxBillingEntryServiceTest {

	private JdbcTemplate jdbcTemplate;

	private AdvertiserScope advertiserScope;

	private OcpxBillingEntryService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxBillingEntryService(jdbcTemplate, advertiserScope);
	}

	@Test
	@SuppressWarnings("unchecked")
	void advertiserScopeAlwaysReplacesTheRequestedLedgerOwner() {
		when(advertiserScope.accountAdvertiserId("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		var result = service.list(1, 30, "advertiser-other", null, "CONVERSION_CHARGE", "REJECTED", null,
				null, null);

		assertThat(result.getTotal()).isZero();
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), args.capture());
		assertThat(args.getValue()).contains("advertiser-own", "CONVERSION_CHARGE", "REJECTED")
			.doesNotContain("advertiser-other");
	}

	@Test
	void invalidFiltersAndReversedTimeRangeAreRejectedBeforeQuerying() {
		assertThatThrownBy(() -> service.list(1, 30, null, null, null, "SUCCESS", null, null, null))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("status");
		assertThatThrownBy(() -> service.list(1, 30, null, null, null, null, null,
				LocalDateTime.of(2026, 8, 18, 0, 0), LocalDateTime.of(2026, 8, 17, 0, 0)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("时间范围无效");
	}

}
