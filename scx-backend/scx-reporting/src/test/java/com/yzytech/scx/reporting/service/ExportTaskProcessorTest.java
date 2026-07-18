package com.yzytech.scx.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.reporting.entity.ExportTask;
import com.yzytech.scx.reporting.entity.ReportDaily;
import com.yzytech.scx.reporting.mapper.ExportTaskMapper;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import com.yzytech.scx.tracking.dto.AttributionReviewView;
import com.yzytech.scx.tracking.mapper.AttributionReviewMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExportTaskProcessorTest {
    private final ExportTaskMapper exportMapper = mock(ExportTaskMapper.class);
    private final ReportDailyMapper dailyMapper = mock(ReportDailyMapper.class);
    private final AttributionReviewMapper reviewMapper = mock(AttributionReviewMapper.class);
    private final UserSubjectScopeMapper scopeMapper = mock(UserSubjectScopeMapper.class);
    private final ExportTaskProcessor processor = new ExportTaskProcessor(exportMapper, dailyMapper, reviewMapper, scopeMapper);

    @Test
    void createsExpiringCsvForCurrentMediaScope() {
        ExportTask task = task();
        ReportDaily row = new ReportDaily();
        row.setReportDate(LocalDate.of(2026, 7, 16));
        row.setMediaId("media_sandbox");
        row.setTaskId("task-1");
        row.setUniqueClickCount(2L);
        row.setEligibleClickCount(1L);
        row.setDeduplicatedClickCount(1L);
        row.setAttributedActivateCount(1L);
        row.setAttributedRegisterCount(0L);
        row.setAttributedPayCount(0L);
        when(exportMapper.claim(eq(task.getId()), any())).thenReturn(1);
        when(exportMapper.selectById(task.getId())).thenReturn(task);
        when(scopeMapper.selectCount(any())).thenReturn(1L);
        when(dailyMapper.selectForExport(eq("media_sandbox"), any(), any(), eq(100_001), eq(false), eq(List.of())))
                .thenReturn(List.of(row));

        processor.process(task.getId());

        ArgumentCaptor<ExportTask> captured = ArgumentCaptor.forClass(ExportTask.class);
        verify(exportMapper).updateById(captured.capture());
        ExportTask completed = captured.getValue();
        assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(completed.getRowCount()).isEqualTo(1);
        assertThat(completed.getExpiresAt()).isNotNull();
        assertThat(new String(completed.getArtifactContent(), StandardCharsets.UTF_8))
                .contains("报告日期").contains("media_sandbox");
    }

    @Test
    void refusesExecutionWhenRequesterLosesMediaScope() {
        ExportTask task = task();
        when(exportMapper.claim(eq(task.getId()), any())).thenReturn(1);
        when(exportMapper.selectById(task.getId())).thenReturn(task);
        when(scopeMapper.selectCount(any())).thenReturn(0L);

        processor.process(task.getId());

        ArgumentCaptor<ExportTask> captured = ArgumentCaptor.forClass(ExportTask.class);
        verify(exportMapper).updateById(captured.capture());
        assertThat(captured.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captured.getValue().getErrorCode()).isEqualTo("SCOPE_REVOKED");
    }

    @Test
    void createsReductionCsvWithoutRawDeviceIdentifiers() {
        ExportTask task = task();
        task.setExportType("REDUCTION_CSV");
        AttributionReviewView row = new AttributionReviewView();
        row.setId("attribution-1");
        row.setTaskCode("task-code");
        row.setTaskName("任务一");
        row.setConversionEventType("ACTIVATE");
        row.setOverLimit(true);
        row.setReviewStatus("REDUCED");
        row.setReviewReason("超量审减");
        when(exportMapper.claim(eq(task.getId()), any())).thenReturn(1);
        when(exportMapper.selectById(task.getId())).thenReturn(task);
        when(scopeMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectReductionForExport(eq("media_sandbox"), any(), any(), eq(100_001), eq(false), eq(List.of())))
                .thenReturn(List.of(row));

        processor.process(task.getId());

        ArgumentCaptor<ExportTask> captured = ArgumentCaptor.forClass(ExportTask.class);
        verify(exportMapper).updateById(captured.capture());
        ExportTask completed = captured.getValue();
        assertThat(completed.getArtifactName()).startsWith("scx-reduction-");
        assertThat(new String(completed.getArtifactContent(), StandardCharsets.UTF_8))
                .contains("超量审减").doesNotContain("oaid").doesNotContain("ip");
    }

    private ExportTask task() {
        ExportTask task = new ExportTask();
        task.setId("export-1");
        task.setRequesterUserId("1");
        task.setMediaId("media_sandbox");
        task.setReportStartDate(LocalDate.of(2026, 7, 16));
        task.setReportEndDate(LocalDate.of(2026, 7, 16));
        task.setStatus("PENDING");
        return task;
    }
}
