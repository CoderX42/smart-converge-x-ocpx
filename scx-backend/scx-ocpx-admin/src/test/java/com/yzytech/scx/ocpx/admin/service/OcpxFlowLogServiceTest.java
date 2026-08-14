package com.yzytech.scx.ocpx.admin.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxFlowLogServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void advertiserScopeOverridesForgedFlowLogFilter() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxFlowLogService(jdbcTemplate, advertiserScope).query(LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 8, 2), null, null, "advertiser-other", null, null, null, null, 1L, 20L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), args.capture());
		assertThat(sql.getValue()).contains("f.advertiser_id=?");
		assertThat(args.getValue()).contains("advertiser-own").doesNotContain("advertiser-other");
	}

}
