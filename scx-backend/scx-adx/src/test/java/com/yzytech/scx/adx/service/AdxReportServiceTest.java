package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.mapper.AdxHourlyReportMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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

	@Test
	void rejectsInvertedDateRangeBeforeQuery() {
		assertThatThrownBy(() -> service.summary(new Page<>(), "DATE", null, null, null, null, LocalDate.of(2026, 8, 3),
				LocalDate.of(2026, 8, 1)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("开始日期不能晚于结束日期");
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
