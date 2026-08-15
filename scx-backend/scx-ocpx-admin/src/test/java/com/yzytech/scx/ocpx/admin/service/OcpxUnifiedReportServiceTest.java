package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class OcpxUnifiedReportServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void dashboardReturnsTheExactCpcAcceptanceNumbersFromTheHourlyTruth() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString(1)).thenReturn("2026081501");
		when(resultSet.getString(2)).thenReturn("2026-08-15 01:00");
		when(resultSet.getLong(3)).thenReturn(100L);
		when(resultSet.getLong(4)).thenReturn(100L);
		when(resultSet.getLong(5)).thenReturn(10L);
		when(resultSet.getLong(6)).thenReturn(10L);
		when(resultSet.getLong(7)).thenReturn(2L);
		when(resultSet.getBigDecimal(22)).thenReturn(new BigDecimal("25.000000"));
		when(resultSet.getBigDecimal(23)).thenReturn(BigDecimal.ZERO);
		when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
			.thenAnswer(invocation -> ((ResultSetExtractor) invocation.getArgument(1)).extractData(resultSet));
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
		LocalDateTime asOf = LocalDateTime.of(2026, 8, 15, 12, 0);
		when(jdbcTemplate.queryForObject(anyString(), eq(LocalDateTime.class), any(Object[].class))).thenReturn(asOf);
		when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> {
				ResultSet accountResult = mock(ResultSet.class);
				when(accountResult.getBigDecimal(1)).thenReturn(new BigDecimal("25.000000"));
				when(accountResult.getLong(2)).thenReturn(0L);
				return ((RowMapper) invocation.getArgument(1)).mapRow(accountResult, 0);
			});
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("adv-1")).thenReturn("adv-1");
		when(advertiserScope.accountAdvertiserId("adv-1")).thenReturn("adv-1");
		OcpxAdvertiserAccountService accountService = mock(OcpxAdvertiserAccountService.class);
		when(accountService.balance("adv-1")).thenReturn(new BigDecimal("75.000000"));
		OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate, advertiserScope, accountService);

		var dashboard = service.dashboard(new UnifiedReportQuery(LocalDate.of(2026, 8, 15),
				LocalDate.of(2026, 8, 15), "TIME", "adv-1", null, null, null, 1, 30));

		assertThat(dashboard.getDataStatus()).isEqualTo("READY");
		assertThat(dashboard.getAsOf()).isEqualTo(asOf);
		assertThat(dashboard.getTimezone()).isEqualTo("Asia/Shanghai");
		assertThat(dashboard.getAccount().getAvailableBalance()).isEqualByComparingTo("75.000000");
		assertThat(dashboard.getAccount().getTodaySpend()).isEqualByComparingTo("25.000000");
		assertThat(dashboard.getMetrics()).hasSize(9);
		assertMetric(dashboard.getMetrics(), "SPEND", "25.000000", "OK");
		assertMetric(dashboard.getMetrics(), "IMPRESSION", "100", "OK");
		assertMetric(dashboard.getMetrics(), "CLICK", "10", "OK");
		assertMetric(dashboard.getMetrics(), "CONVERSION", "2", "OK");
		assertMetric(dashboard.getMetrics(), "CTR", "10.000000", "OK");
		assertMetric(dashboard.getMetrics(), "CVR", "20.000000", "OK");
		assertMetric(dashboard.getMetrics(), "CPM", "250.000000", "OK");
		assertMetric(dashboard.getMetrics(), "CPC", "2.500000", "OK");
		assertMetric(dashboard.getMetrics(), "CPA", "12.500000", "OK");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void emptyDashboardReturnsRealZeroCountsAndExplicitZeroDenominators() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(false);
		when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
			.thenAnswer(invocation -> ((ResultSetExtractor) invocation.getArgument(1)).extractData(resultSet));
		when(jdbcTemplate.queryForObject(anyString(), eq(LocalDateTime.class), any(Object[].class))).thenReturn(null);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain(nullable(String.class))).thenReturn(null);
		OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate, advertiserScope,
				mock(OcpxAdvertiserAccountService.class));

		var dashboard = service.dashboard(new UnifiedReportQuery(LocalDate.of(2026, 8, 15),
				LocalDate.of(2026, 8, 15), "TIME", null, null, null, null, 1, 30));

		assertThat(dashboard.getDataStatus()).isEqualTo("EMPTY");
		assertThat(dashboard.getAccount()).isNull();
		assertMetric(dashboard.getMetrics(), "IMPRESSION", "0", "OK");
		assertMetric(dashboard.getMetrics(), "CLICK", "0", "OK");
		assertMetric(dashboard.getMetrics(), "CONVERSION", "0", "OK");
		for (String code : List.of("CTR", "CVR", "CPM", "CPC", "CPA")) {
			assertThat(dashboard.getMetrics().get(code).getValue()).isNull();
			assertThat(dashboard.getMetrics().get(code).getStatus()).isEqualTo("ZERO_DENOMINATOR");
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void dashboardWithoutDatesDefaultsToTheServerBusinessDate() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(false);
		when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
			.thenAnswer(invocation -> ((ResultSetExtractor) invocation.getArgument(1)).extractData(resultSet));
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain(nullable(String.class))).thenReturn(null);
		OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate, advertiserScope,
				mock(OcpxAdvertiserAccountService.class));

		var dashboard = service.dashboard(null);

		LocalDate businessDate = LocalDate.now(OcpxUnifiedReportService.BUSINESS_ZONE);
		assertThat(dashboard.getBusinessDate()).isEqualTo(businessDate);
		assertThat(dashboard.getStartDate()).isEqualTo(businessDate);
		assertThat(dashboard.getEndDate()).isEqualTo(businessDate);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void advertiserOptionsUseOnlyOwnedEntities() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		OcpxAdvertiserAccountService advertiserAccountService = mock(OcpxAdvertiserAccountService.class);
		when(advertiserScope.currentAdvertiserId()).thenReturn("advertiser-own");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxUnifiedReportService(jdbcTemplate, advertiserScope, advertiserAccountService).options();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, times(4)).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getAllValues()).allMatch(value -> value.contains("advertiser_id=?") || value.contains("id=?"));
		assertThat(args.getAllValues()).allMatch(value -> List.of(value).contains("advertiser-own"));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void readsTheAuthoritativeHourlyMetricsWithoutImplicitPriceConversion() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString(1)).thenReturn("2026081201");
		when(resultSet.getString(2)).thenReturn("2026-08-12 01:00");
		for (int index = 3; index <= 21; index++) {
			when(resultSet.getLong(index)).thenReturn((long) index - 2);
		}
		when(resultSet.getBigDecimal(22)).thenReturn(new BigDecimal("25.000000"));
		when(resultSet.getBigDecimal(23)).thenReturn(new BigDecimal("123.45"));
		when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
			.thenAnswer(invocation -> ((ResultSetExtractor) invocation.getArgument(1)).extractData(resultSet));
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-id")).thenReturn("advertiser-id");
		when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
				any(Object[].class))).thenReturn(1L);
		OcpxAdvertiserAccountService advertiserAccountService = mock(OcpxAdvertiserAccountService.class);
		OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate, advertiserScope,
				advertiserAccountService);
		UnifiedReportQuery query = new UnifiedReportQuery(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12), "TIME",
				"advertiser-id", "link-id", "media-id", "task-id", 1, 20);

		var page = service.query(query);

		assertThat(page.getTotal()).isEqualTo(1);
		assertThat(page.getRecords()).hasSize(1);
		assertThat(page.getRecords().get(0).getMetrics()).hasSize(21);
		assertThat(page.getRecords().get(0).getMetrics().get("SHOW").getCount()).isEqualTo(1);
		assertThat(page.getRecords().get(0).getMetrics().get("FINISH_THE_ORDER").getCount()).isEqualTo(16);
		assertThat(page.getRecords().get(0).getMetrics().get("FIRST_ACTIVATION").getCount()).isEqualTo(17);
		assertThat(page.getRecords().get(0).getMetrics().get("FIRST_ORDER").getCount()).isEqualTo(18);
		assertThat(page.getRecords().get(0).getMetrics().get("BASE_PRICE").getAmount())
			.isEqualByComparingTo(new BigDecimal("25.000000"));
		assertThat(page.getRecords().get(0).getMetrics().get("CURRENCY_AMOUNT").getAmount())
			.isEqualByComparingTo(new BigDecimal("123.45"));
		assertThat(page.getSummary().keySet()).containsAll(
				List.of("SHOW", "UNIQUE_SHOW", "CLICK", "UNIQUE_CLICK", "ACTIVATE", "EVOKE", "LOGIN", "REGISTER",
						"PAYMENT", "CUSTOMER_ACQUISITION", "NEXT_STAY", "VISITS", "AUTHENTICATION", "PLACE_ORDER",
						"FINISH_THE_ORDER", "FIRST_ACTIVATION", "FIRST_ORDER", "TARGET_CONVERSION",
						"REJECTED_CLICK", "BASE_PRICE", "CURRENCY_AMOUNT"));
	}

	private void assertMetric(Map<String, com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews.DashboardMetric> metrics,
			String code, String expected, String status) {
		assertThat(metrics.get(code).getValue()).isEqualByComparingTo(expected);
		assertThat(metrics.get(code).getStatus()).isEqualTo(status);
	}

}
