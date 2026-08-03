package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.mapper.AdxHourlyReportMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
		when(mapper.dashboardTotals(any(), any(), any(), any(), any()))
			.thenReturn(Map.of("yesterdayIncome", BigDecimal.ONE, "sevenDayIncome", BigDecimal.TEN, "thirtyDayIncome",
					BigDecimal.TEN, "totalIncome", BigDecimal.valueOf(100)));
		when(mapper.dashboardTrend(any(), any())).thenReturn(List.of());

		Map<String, Object> result = service.dashboard();

		assertThat(result).containsEntry("yesterdayIncome", BigDecimal.ONE)
			.containsEntry("totalIncome", BigDecimal.valueOf(100))
			.containsEntry("trend", List.of());
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
