package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.mapper.AdxHourlyReportMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.yzytech.scx.adx.dto.DashboardDailyRow;
import com.yzytech.scx.adx.dto.DashboardViews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

class AdxReportServiceTest {

	private final AdxHourlyReportMapper mapper = mock(AdxHourlyReportMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxReportService service = new AdxReportService(mapper, accessScope);

	@Test
	void rejectsInvertedDateRangeBeforeQuery() {
		assertThatThrownBy(() -> service.summary(new Page<>(), "DATE", null, null, null, null, LocalDate.of(2026, 8, 3),
				LocalDate.of(2026, 8, 1)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("开始日期不能晚于结束日期");
	}

	@Test
	void dashboardReturnsStableZeroSafeContract() {
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		DashboardViews.Summary result = service.dashboard(null, null, null, null, null, null);

		assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
		assertThat(result.trend()).hasSize(30);
		assertThat(result.totals().clicks()).isZero();
		assertThat(result.totals().activationRate()).isNull();
		assertThat(result.highlights().todayClicks().changeRate()).isNull();
	}

	@Test
	void dashboardMarksHistoricalClickCoverageAsUnavailable() {
		DashboardDailyRow row = new DashboardDailyRow();
		row.setReportDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
		row.setClicks(10L);
		row.setClassifiedClicks(8L);
		row.setEligibleClicks(7L);
		row.setActivates(3L);
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any())).thenReturn(List.of(row));

		DashboardViews.Summary result = service.dashboard(null, null, null, null, null, null);

		assertThat(result.dataCoverage().clickQualityComplete()).isFalse();
		assertThat(result.dataCoverage().rateStatus()).isEqualTo("INCOMPLETE_HISTORY");
		assertThat(result.totals().activationRate()).isNull();
	}

	@Test
	void dashboardRejectsMoreThanNinetyNaturalDays() {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
		assertThatThrownBy(() -> service.dashboard(today.minusDays(90), today, null, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("仪表盘单次查询范围不能超过90天");
	}

	@Test
	void dashboardForcesAdvertiserScopeAcrossAllDimensions() {
		when(accessScope.constrainAdvertiser("forged-advertiser")).thenReturn("own-advertiser");
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		service.dashboard(null, null, "channel-1", "forged-advertiser", "app-1", "monitor-1");

		verify(mapper).dashboardDaily(any(), any(), eq("channel-1"), eq("own-advertiser"), eq("app-1"),
				eq("monitor-1"));
	}

	@Test
	void forcesAdvertiserUsersToTheirOwnReportScope() {
		when(accessScope.constrainAdvertiser("other-advertiser")).thenReturn("own-advertiser");
		when(mapper.summary(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new Page<>());

		service.summary(new Page<>(), "DATE", null, null, "other-advertiser", null, LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 8, 2));

		verify(mapper).summary(any(), eq("DATE"), any(), any(), eq("own-advertiser"), any(), any(), any());
	}

}
