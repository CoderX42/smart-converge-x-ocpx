package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.reporting.entity.ReportReconciliationIssue;
import com.yzytech.scx.reporting.entity.ReportRun;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.reporting.mapper.ReportHourlyMapper;
import com.yzytech.scx.reporting.mapper.ReportLeaseMapper;
import com.yzytech.scx.reporting.mapper.ReportReconciliationIssueMapper;
import com.yzytech.scx.reporting.mapper.ReportRunMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportPipelineService {

	private final MediaScopeService scopeService;

	private final ReportRunMapper runMapper;

	private final MediaMapper mediaMapper;

	private final ReportHourlyMapper hourlyMapper;

	private final ReportDailyMapper dailyMapper;

	private final ReportReconciliationIssueMapper issueMapper;

	private final ReportLeaseMapper leaseMapper;

	private final TrackingProperties trackingProperties;

	private final String leaseOwner = "report-aggregate-" + UUID.randomUUID();

	public ReportRun refresh(LocalDate date, String mediaId, String runType) {
		if (!scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
		return refreshSystem(date, mediaId, runType);
	}

	public ReportRun refreshSystem(LocalDate date, String mediaId, String runType) {
		if (date == null || mediaId == null || mediaId.isBlank())
			throw new IllegalArgumentException("报告日期和媒体不能为空");
		ReportRun previous = runMapper.selectLatestRun(date, mediaId);
		int revision = previous == null || previous.getRevision() == null ? 1 : previous.getRevision() + 1;
		ZoneId reportZone = reportZone(mediaId);
		String dataStatus = date.isAfter(LocalDate.now(reportZone).minusDays(7)) ? "PRELIMINARY" : "FINAL";
		LocalDateTime startUtc = date.atStartOfDay(reportZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
		LocalDateTime endUtc = date.plusDays(1)
			.atStartOfDay(reportZone)
			.withZoneSameInstant(ZoneOffset.UTC)
			.toLocalDateTime();
		LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC);
		String sourceHash = hourlyMapper.selectSourceHash(mediaId, startUtc, endUtc);
		if (previous != null && sourceHash != null && sourceHash.equals(previous.getInputSnapshotHash())
				&& (dataStatus.equals(previous.getDataStatus()) || "INCOMPLETE".equals(previous.getDataStatus()))
				&& !"BACKFILL".equals(runType))
			return previous;
		if (previous != null && "FINAL".equals(previous.getDataStatus()) && sourceHash != null
				&& !sourceHash.equals(previous.getInputSnapshotHash()) && !"BACKFILL".equals(runType)) {
			recordLateAfterFinal(date, mediaId, previous, sourceHash);
			return previous;
		}
		String leaseKey = "daily|" + mediaId + "|" + date;
		LocalDateTime leaseUntil = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30);
		leaseMapper.insertIfAbsent(leaseKey, leaseOwner, leaseUntil);
		leaseMapper.renewOrAcquire(leaseKey, leaseOwner, leaseUntil);
		if (leaseMapper.isOwned(leaseKey, leaseOwner) != 1) {
			if (previous != null)
				return previous;
			throw new IllegalStateException("该媒体日期正在由其他实例聚合");
		}
		ReportRun run = new ReportRun();
		run.setReportDate(date);
		run.setTimezone(reportZone.getId());
		run.setMediaId(mediaId);
		run.setGranularity("DAILY");
		run.setRunType(runType == null ? "MANUAL" : runType);
		run.setRevision(revision);
		run.setDataStatus(dataStatus);
		run.setRowCount(0);
		run.setDataOrigin("LIVE");
		run.setInputCutoffReceivedAt(cutoff);
		run.setInputSnapshotHash(sourceHash);
		run.setRuleVersion(trackingProperties.getAttributionRuleVersion());
		run.setStatus("RUNNING");
		run.setStartedAt(cutoff);
		runMapper.insert(run);
		try {
			int hourlyRows = hourlyMapper.aggregate(run.getId(), date, mediaId, startUtc, endUtc, revision, dataStatus,
					reportZone.getId());
			int dailyRows = dailyMapper.aggregateFromHourly(run.getId(), date, mediaId, revision, dataStatus,
					reportZone.getId());
			run.setRowCount(dailyRows);
			run.setSourceWatermark(hourlyMapper.selectSourceWatermark(mediaId, startUtc, endUtc));
			run.setDataStatus(dailyRows == 0 ? "SUCCEEDED_EMPTY" : dailyMapper.selectRunDataStatus(run.getId()));
			run.setStatus("SUCCEEDED");
			run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			if (hourlyRows == 0 && dailyRows != 0)
				throw new IllegalStateException("小时与日报行数不一致");
			runMapper.updateById(run);
			return run;
		}
		catch (RuntimeException exception) {
			run.setStatus("FAILED");
			run.setFailureReason(exception.getMessage() == null ? exception.getClass().getSimpleName()
					: exception.getMessage().substring(0, Math.min(512, exception.getMessage().length())));
			run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			runMapper.updateById(run);
			throw exception;
		}
		finally {
			leaseMapper.release(leaseKey, leaseOwner);
		}
	}

	private ZoneId reportZone(String mediaId) {
		Media media = mediaMapper.selectById(mediaId);
		try {
			return ZoneId.of(media == null || media.getTimezone() == null || media.getTimezone().isBlank()
					? "Asia/Shanghai" : media.getTimezone());
		}
		catch (RuntimeException ignored) {
			return ZoneId.of("Asia/Shanghai");
		}
	}

	private void recordLateAfterFinal(LocalDate date, String mediaId, ReportRun previous, String sourceHash) {
		if (issueMapper.selectCount(Wrappers.<ReportReconciliationIssue>lambdaQuery()
			.eq(ReportReconciliationIssue::getMediaId, mediaId)
			.eq(ReportReconciliationIssue::getReportDate, date)
			.eq(ReportReconciliationIssue::getIssueType, "LATE_AFTER_FINAL")
			.eq(ReportReconciliationIssue::getStatus, "OPEN")) > 0)
			return;
		ReportReconciliationIssue issue = new ReportReconciliationIssue();
		issue.setMediaId(mediaId);
		issue.setReportDate(date);
		issue.setIssueType("LATE_AFTER_FINAL");
		issue.setResourceId(previous.getId());
		issue.setDetailJson(
				"{\"previousHash\":\"" + previous.getInputSnapshotHash() + "\",\"newHash\":\"" + sourceHash + "\"}");
		issue.setStatus("OPEN");
		issue.setDetectedAt(LocalDateTime.now(ZoneOffset.UTC));
		issueMapper.insert(issue);
	}

	@Scheduled(cron = "0 20 * * * *", zone = "Asia/Shanghai")
	public void aggregateCurrentDates() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
		for (String mediaId : activeMediaIds()) {
			safeRefresh(today, mediaId);
			safeRefresh(today.minusDays(1), mediaId);
		}
	}

	@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai")
	public void reviseOpenWindow() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
		for (String mediaId : activeMediaIds())
			for (int days = 1; days <= 7; days++)
				safeRefresh(today.minusDays(days), mediaId);
	}

	private java.util.List<String> activeMediaIds() {
		return runMapper.selectActiveMediaIds();
	}

	private void safeRefresh(LocalDate date, String mediaId) {
		try {
			refreshSystem(date, mediaId, "AUTO");
		}
		catch (RuntimeException ignored) {
			// The failed run is persisted and the next media/date must continue.
		}
	}

}
