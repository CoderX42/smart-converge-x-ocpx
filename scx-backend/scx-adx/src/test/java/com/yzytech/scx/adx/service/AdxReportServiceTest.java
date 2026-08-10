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

import java.util.Map;

class AdxReportServiceTest {

	private final AdxHourlyReportMapper mapper = mock(AdxHourlyReportMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxReportService service = new AdxReportService(mapper, accessScope);

	private AdxAccessScope.Snapshot allScope() {
		return new AdxAccessScope.Snapshot(1L, AdxAccessScope.PermissionLevel.MANAGE, Map.of());
	}

	@Test
	void rejectsInvertedDateRangeBeforeQuery() {
		assertThatThrownBy(() -> service.summary(new Page<>(), "DATE", null, null, null, null, LocalDate.of(2026, 8, 3),
				LocalDate.of(2026, 8, 1)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("开始日期不能晚于结束日期");
	}

	@Test
	void dashboardReturnsStableZeroSafeContract() {
		when(accessScope.current()).thenReturn(allScope());
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		DashboardViews.Summary result = service.dashboard(null, null, null, null, null, null);

		assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
		assertThat(result.trend()).hasSize(30);
		assertThat(result.totals().clicks()).isZero();
		assertThat(result.totals().activationRate()).isNull();
		assertThat(result.highlights().todayClicks().changeRate()).isNull();
	}

	@Test
	void dashboardMarksHistoricalClickCoverageAsUnavailable() {
		when(accessScope.current()).thenReturn(allScope());
		DashboardDailyRow row = new DashboardDailyRow();
		row.setReportDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
		row.setClicks(10L);
		row.setClassifiedClicks(8L);
		row.setEligibleClicks(7L);
		row.setActivates(3L);
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(row));

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
		AdxAccessScope.Snapshot limited = new AdxAccessScope.Snapshot(1L, null,
				Map.of(AdxAccessScope.ScopeType.ADVERTISER,
						Map.of("own-advertiser", AdxAccessScope.PermissionLevel.VIEW), AdxAccessScope.ScopeType.CHANNEL,
						Map.of("channel-1", AdxAccessScope.PermissionLevel.VIEW)));
		when(accessScope.current()).thenReturn(limited);
		when(mapper.dashboardDaily(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		service.dashboard(null, null, "channel-1", "own-advertiser", "app-1", "monitor-1");

		verify(mapper).dashboardDaily(any(), any(), eq("channel-1"), eq("own-advertiser"), eq("app-1"), eq("monitor-1"),
				eq(List.of("own-advertiser")), eq(List.of("channel-1")));
	}

	@Test
	void forcesAdvertiserUsersToTheirOwnReportScope() {
		AdxAccessScope.Snapshot limited = new AdxAccessScope.Snapshot(1L, null,
				Map.of(AdxAccessScope.ScopeType.ADVERTISER,
						Map.of("own-advertiser", AdxAccessScope.PermissionLevel.VIEW), AdxAccessScope.ScopeType.CHANNEL,
						Map.of("channel-1", AdxAccessScope.PermissionLevel.VIEW)));
		when(accessScope.current()).thenReturn(limited);
		when(mapper.summary(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(new Page<>());

		service.summary(new Page<>(), "DATE", null, null, "own-advertiser", null, LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 8, 2));

		verify(mapper).summary(any(), eq("DATE"), any(), any(), eq("own-advertiser"), any(),
				eq(List.of("own-advertiser")), eq(List.of("channel-1")), any(), any());
	}

}
