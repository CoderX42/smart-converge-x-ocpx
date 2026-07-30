package com.yzytech.scx.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzytech.scx.reporting.entity.ReportReconciliationIssue;
import com.yzytech.scx.reporting.entity.ReportRun;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.reporting.mapper.ReportHourlyMapper;
import com.yzytech.scx.reporting.mapper.ReportLeaseMapper;
import com.yzytech.scx.reporting.mapper.ReportReconciliationIssueMapper;
import com.yzytech.scx.reporting.mapper.ReportRunMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportPipelineServiceTest {

	private final ReportRunMapper runMapper = mock(ReportRunMapper.class);

	private final ReportHourlyMapper hourlyMapper = mock(ReportHourlyMapper.class);

	private final ReportDailyMapper dailyMapper = mock(ReportDailyMapper.class);

	private final ReportReconciliationIssueMapper issueMapper = mock(ReportReconciliationIssueMapper.class);

	private final ReportLeaseMapper leaseMapper = mock(ReportLeaseMapper.class);

	private final ReportPipelineService service = new ReportPipelineService(mock(MediaScopeService.class), runMapper,
			mock(MediaMapper.class), hourlyMapper, dailyMapper, issueMapper, leaseMapper, new TrackingProperties());

	@Test
	void unchangedSourcesDoNotCreateAnotherRevision() {
		LocalDate date = LocalDate.now().minusDays(1);
		ReportRun previous = successful(date, "PRELIMINARY", "same-hash");
		when(runMapper.selectLatestRun(date, "media-1")).thenReturn(previous);
		when(hourlyMapper.selectSourceHash(org.mockito.ArgumentMatchers.eq("media-1"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn("same-hash");

		assertThat(service.refreshSystem(date, "media-1", "AUTO")).isSameAs(previous);
		verify(runMapper, never()).insert(org.mockito.ArgumentMatchers.any(ReportRun.class));
	}

	@Test
	void changedSourcesAfterFinalBecomeReconciliationIssue() {
		LocalDate date = LocalDate.now().minusDays(8);
		ReportRun previous = successful(date, "FINAL", "old-hash");
		when(runMapper.selectLatestRun(date, "media-1")).thenReturn(previous);
		when(hourlyMapper.selectSourceHash(org.mockito.ArgumentMatchers.eq("media-1"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn("new-hash");
		when(issueMapper.selectCount(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ReportReconciliationIssue>>any()))
			.thenReturn(0L);

		assertThat(service.refreshSystem(date, "media-1", "AUTO")).isSameAs(previous);
		ArgumentCaptor<ReportReconciliationIssue> issue = ArgumentCaptor.forClass(ReportReconciliationIssue.class);
		verify(issueMapper).insert(issue.capture());
		assertThat(issue.getValue().getIssueType()).isEqualTo("LATE_AFTER_FINAL");
		verify(runMapper, never()).insert(org.mockito.ArgumentMatchers.any(ReportRun.class));
	}

	private ReportRun successful(LocalDate date, String dataStatus, String hash) {
		ReportRun run = new ReportRun();
		run.setId("run-1");
		run.setReportDate(date);
		run.setRevision(1);
		run.setStatus("SUCCEEDED");
		run.setDataStatus(dataStatus);
		run.setInputSnapshotHash(hash);
		return run;
	}

}
