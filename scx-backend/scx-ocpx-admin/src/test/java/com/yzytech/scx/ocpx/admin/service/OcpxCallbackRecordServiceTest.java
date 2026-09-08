package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxCallbackRecordSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.CallbackRecordViews;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
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

class OcpxCallbackRecordServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void readsAuthoritativeCallbackFactsWithScopedFiltersAndPagination() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
		ResultSet rs = row();
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));

		CallbackRecordViews.Page page = new OcpxCallbackRecordService(
				XmlMapperTestSupport.mapper(OcpxCallbackRecordSqlMapper.class, jdbcTemplate), advertiserScope)
			.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), "advertiser-other", "link-1", "media-1",
					"task-1", "activate", true, "passed", "request-1", 2L, 10L);

		assertThat(page.getTotal()).isEqualTo(1);
		assertThat(page.getRecords()).singleElement().satisfies(record -> {
			assertThat(record.getRequestId()).isEqualTo("request-1");
			assertThat(record.getEventCode()).isEqualTo("ACTIVATE");
			assertThat(record.isTargetConversion()).isTrue();
			assertThat(record.getDeductionDecision()).isEqualTo("PASSED");
			assertThat(record.getDeductionRatioSnapshot()).isEqualByComparingTo("0.7");
		});
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("f.flow_type='CALLBACK'", "f.target_conversion=?",
				"LEFT JOIN scx_ocpx_advertiser", "FORCE INDEX (idx_ocpx_event_fact_callback_page)",
				"ORDER BY f.received_at DESC,f.id DESC", "LIMIT ? OFFSET ?");
		ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
		assertThat(countSql.getValue()).contains("FORCE INDEX (idx_ocpx_event_fact_callback_page)");
		assertThat(args.getValue())
			.contains("advertiser-own", "link-1", "media-1", "task-1", "ACTIVATE", "PASSED", "request-1", true, 10L,
					10L)
			.doesNotContain("advertiser-other");
	}

	@Test
	void rejectsUnknownEventDecisionAndExcessiveDateRange() {
		OcpxCallbackRecordService service = new OcpxCallbackRecordService(
				XmlMapperTestSupport.mapper(OcpxCallbackRecordSqlMapper.class, mock(JdbcTemplate.class)),
				mock(AdvertiserScope.class));
		assertThatThrownBy(() -> service.query(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1), null, null, null,
				null, null, null, null, null, null, null))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("时间范围");
		assertThatThrownBy(() -> service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, null,
				null, "UNKNOWN", null, null, null, null, null))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("eventCode");
		assertThatThrownBy(() -> service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, null,
				null, null, null, "FORWARDED", null, null, null))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("deductionDecision");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void filtersHistoricalFactsWithoutCallingThemPending() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain(null)).thenReturn(null);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxCallbackRecordService(XmlMapperTestSupport.mapper(OcpxCallbackRecordSqlMapper.class, jdbcTemplate),
				advertiserScope)
			.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, null, null, null, null,
					"UNAVAILABLE", null, 1L, 10L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("f.deduction_decision IS NULL").doesNotContain("f.deduction_decision=?");
	}

	private ResultSet row() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("id")).thenReturn("fact-1");
		when(rs.getString("request_id")).thenReturn("request-1");
		when(rs.getString("event_code")).thenReturn("ACTIVATE");
		when(rs.getBoolean("target_conversion")).thenReturn(true);
		when(rs.getString("advertiser_id")).thenReturn("advertiser-own");
		when(rs.getString("advertiser_name")).thenReturn("广告主一");
		when(rs.getString("promotion_link_id")).thenReturn("link-1");
		when(rs.getString("link_name")).thenReturn("链接一");
		when(rs.getString("media_id")).thenReturn("media-1");
		when(rs.getString("media_name")).thenReturn("媒体一");
		when(rs.getString("monitor_task_id")).thenReturn("task-1");
		when(rs.getString("monitor_name")).thenReturn("任务一");
		when(rs.getString("billing_status")).thenReturn("POSTED");
		when(rs.getBigDecimal("spend_amount")).thenReturn(new BigDecimal("2.00"));
		when(rs.getBigDecimal("currency_amount")).thenReturn(new BigDecimal("9.99"));
		when(rs.getString("deduction_decision")).thenReturn("PASSED");
		when(rs.getBigDecimal("deduction_ratio_snapshot")).thenReturn(new BigDecimal("0.7"));
		when(rs.getTimestamp("deduction_decided_at"))
			.thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 12, 1)));
		when(rs.getTimestamp("received_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 12, 0)));
		return rs;
	}

}
