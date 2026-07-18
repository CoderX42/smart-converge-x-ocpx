package com.yzytech.scx.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.mapper.EventDetailMapper;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.reporting.mapper.ReportRunMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportingServiceTest {
    private final MediaScopeService scopeService = mock(MediaScopeService.class);
    private final ReportRunMapper runMapper = mock(ReportRunMapper.class);
    private final ReportDailyMapper dailyMapper = mock(ReportDailyMapper.class);
    private final EventDetailMapper eventDetailMapper = mock(EventDetailMapper.class);
    private final TaskScopeService taskScopeService = mock(TaskScopeService.class);
    private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);
    private final ReportingService service = new ReportingService(scopeService, new TrackingProperties(), runMapper, dailyMapper,
            eventDetailMapper, taskScopeService, taskMapper);

    @Test
    void returnsLatestSnapshotsGroupedByRequestedDimension() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 7);
        ReportBreakdown row = new ReportBreakdown();
        row.setDimensionId("task-1");
        when(taskScopeService.accessForMedia("media-1", false)).thenReturn(TaskScopeService.TaskAccess.unrestricted());
        when(dailyMapper.selectBreakdown("media-1", start, end, "TASK", null, null, null, null, null, null, false,
                List.of())).thenReturn(List.of(row));

        assertThat(service.breakdown(start, end, "media-1", "task")).containsExactly(row);
        verify(dailyMapper).selectBreakdown("media-1", start, end, "TASK", null, null, null, null, null, null, false,
                List.of());
    }

    @Test
    void rejectsUnsafeDimensionAndOversizedDateRangeBeforeQuerying() {
        when(taskScopeService.accessForMedia("media-1", false)).thenReturn(TaskScopeService.TaskAccess.unrestricted());
        LocalDate start = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> service.breakdown(start, start.plusDays(31), "media-1", "task"))
                .hasMessage("单次多维分析最多查询 31 天");
        assertThatThrownBy(() -> service.breakdown(start, start, "media-1", "unknown"))
                .hasMessage("不支持的分析维度");
    }

    @Test
    void appliesTaskScopeToBreakdownAndOverview() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        TaskScopeService.TaskAccess access = TaskScopeService.TaskAccess.restricted(List.of("task-1"));
        when(taskScopeService.accessForMedia("media-1", false)).thenReturn(access);

        service.breakdown(date, date, "media-1", "product");
        service.overview(date, "media-1");

        verify(dailyMapper).selectBreakdown("media-1", date, date, "PRODUCT", null, null, null, null, null, null, true,
                List.of("task-1"));
        verify(dailyMapper).selectTrend("media-1", date.minusDays(6), date, true, List.of("task-1"));
    }

    @Test
    void forwardsOperationalFiltersToBreakdownQuery() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        when(taskScopeService.accessForMedia("media-1", false)).thenReturn(TaskScopeService.TaskAccess.unrestricted());

        service.breakdown(date, date, "media-1", "LINK", "customer-1", "product-1", "link-1", "task-1",
                "channel-1", "document-1");

        verify(dailyMapper).selectBreakdown("media-1", date, date, "LINK", "customer-1", "product-1", "link-1",
                "task-1", "channel-1", "document-1", false, List.of());
    }
}
