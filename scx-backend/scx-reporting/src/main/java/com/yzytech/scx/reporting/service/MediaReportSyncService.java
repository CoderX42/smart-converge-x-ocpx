package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.campaign.entity.AdGroup;
import com.yzytech.scx.campaign.entity.MediaPublishingBinding;
import com.yzytech.scx.campaign.entity.OptimizationGoal;
import com.yzytech.scx.campaign.mapper.AdGroupMapper;
import com.yzytech.scx.campaign.mapper.MediaPublishingBindingMapper;
import com.yzytech.scx.campaign.mapper.OptimizationGoalMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.inventory.adapter.MediaAdapter;
import com.yzytech.scx.inventory.adapter.MediaAdapterRegistry;
import com.yzytech.scx.inventory.dto.ReportPullResult;
import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.reporting.entity.MediaReportHourlyFact;
import com.yzytech.scx.reporting.entity.MediaReportSyncRun;
import com.yzytech.scx.reporting.entity.ReportReconciliationIssue;
import com.yzytech.scx.reporting.mapper.MediaReportHourlyFactMapper;
import com.yzytech.scx.reporting.mapper.MediaReportSyncRunMapper;
import com.yzytech.scx.reporting.mapper.ReportReconciliationIssueMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MediaReportSyncService {

	private static final int MAX_PAGES = 100;

	private final String leaseOwner = "report-sync-" + UUID.randomUUID();

	private final MediaAccountMapper accountMapper;

	private final MediaPublishingBindingMapper bindingMapper;

	private final AdGroupMapper adGroupMapper;

	private final OptimizationGoalMapper optimizationGoalMapper;

	private final MediaAdapterRegistry adapterRegistry;

	private final MediaReportSyncRunMapper runMapper;

	private final MediaReportHourlyFactMapper factMapper;

	private final ReportReconciliationIssueMapper issueMapper;

	private final MediaScopeService scopeService;

	public List<MediaReportSyncRun> sync(String mediaAccountId, LocalDateTime start, LocalDateTime end) {
		MediaAccount account = requireAccount(mediaAccountId, true);
		if (start == null || end == null || !start.isBefore(end))
			throw new IllegalArgumentException("请选择有效的同步时间范围");
		if (start.plusDays(31).isBefore(end))
			throw new IllegalArgumentException("单次媒体同步最多 31 天");
		MediaAdapter adapter = adapterRegistry.resolve(mediaAccountId);
		if (!adapter.getCapabilities().supportsReportPull())
			throw new IllegalArgumentException("媒体适配器不支持报表拉取");
		String trigger = "MANUAL-"
				+ LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
		return List.of(syncType(account, adapter, "PERFORMANCE", start, end, trigger),
				syncType(account, adapter, "BILLING", start, end, trigger));
	}

	public Page<MediaReportSyncRun> page(Page<MediaReportSyncRun> page, String mediaAccountId, String status) {
		MediaAccount account = requireAccount(mediaAccountId, false);
		return runMapper.selectPage(page,
				Wrappers.<MediaReportSyncRun>lambdaQuery()
					.eq(MediaReportSyncRun::getMediaAccountId, account.getId())
					.eq(StringUtils.hasText(status), MediaReportSyncRun::getStatus, status)
					.orderByDesc(MediaReportSyncRun::getStartedAt));
	}

	@Scheduled(cron = "0 10 * * * *", zone = "Asia/Shanghai")
	public void syncPreviousHour() {
		for (MediaAccount account : accountMapper
			.selectList(Wrappers.<MediaAccount>lambdaQuery().eq(MediaAccount::getStatus, "ACTIVE"))) {
			try {
				MediaAdapter adapter = adapterRegistry.resolve(account.getId());
				if (!adapter.getCapabilities().supportsReportPull())
					continue;
				ZoneId zone = zone(account);
				LocalDateTime end = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0);
				LocalDateTime start = end.minusHours(1);
				String trigger = "HOURLY-" + end.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
				syncType(account, adapter, "PERFORMANCE", start, end, trigger);
				syncType(account, adapter, "BILLING", start, end, trigger);
			}
			catch (RuntimeException ignored) {
				// Every run records its own controlled failure and the next account must
				// continue.
			}
		}
	}

	@Scheduled(cron = "0 10 1 * * *", zone = "Asia/Shanghai")
	public void resyncRevisionWindow() {
		for (MediaAccount account : accountMapper
			.selectList(Wrappers.<MediaAccount>lambdaQuery().eq(MediaAccount::getStatus, "ACTIVE"))) {
			try {
				MediaAdapter adapter = adapterRegistry.resolve(account.getId());
				if (!adapter.getCapabilities().supportsReportPull())
					continue;
				ZoneId zone = zone(account);
				LocalDateTime end = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0);
				LocalDateTime start = end.minusDays(7);
				String trigger = "REVISION-" + end.toLocalDate();
				syncType(account, adapter, "PERFORMANCE", start, end, trigger);
				syncType(account, adapter, "BILLING", start, end, trigger);
			}
			catch (RuntimeException ignored) {
				// Failed runs remain visible and retryable without blocking other
				// accounts.
			}
		}
	}

	private MediaReportSyncRun syncType(MediaAccount account, MediaAdapter adapter, String type, LocalDateTime start,
			LocalDateTime end, String trigger) {
		String requestHash = SignUtils.sha256(account.getId() + "|" + type + "|" + start + "|" + end + "|" + trigger);
		MediaReportSyncRun run = runMapper.selectOne(Wrappers.<MediaReportSyncRun>lambdaQuery()
			.eq(MediaReportSyncRun::getMediaAccountId, account.getId())
			.eq(MediaReportSyncRun::getSyncType, type)
			.eq(MediaReportSyncRun::getRequestHash, requestHash)
			.last("LIMIT 1"));
		if (run != null && ("SUCCEEDED".equals(run.getStatus()) || "SUCCEEDED_EMPTY".equals(run.getStatus())))
			return run;
		if (run == null) {
			run = new MediaReportSyncRun();
			run.setMediaAccountId(account.getId());
			run.setSyncType(type);
			run.setWindowStart(toUtc(start, zone(account)));
			run.setWindowEnd(toUtc(end, zone(account)));
			run.setRequestHash(requestHash);
			run.setFetchedRows(0);
			run.setPersistedRows(0);
			run.setStatus("RUNNING");
			run.setLockedBy(leaseOwner);
			run.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
			run.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
			try {
				runMapper.insert(run);
			}
			catch (DuplicateKeyException duplicate) {
				run = runMapper.selectOne(Wrappers.<MediaReportSyncRun>lambdaQuery()
					.eq(MediaReportSyncRun::getMediaAccountId, account.getId())
					.eq(MediaReportSyncRun::getSyncType, type)
					.eq(MediaReportSyncRun::getRequestHash, requestHash)
					.last("LIMIT 1"));
			}
		}
		if (!leaseOwner.equals(run.getLockedBy()) && runMapper.tryAcquire(run.getId(), leaseOwner,
				LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)) != 1)
			return run;
		run.setLockedBy(leaseOwner);
		run.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
		run.setStatus("RUNNING");
		run.setErrorCode(null);
		run.setErrorMessage(null);
		run.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
		runMapper.updateById(run);
		try {
			String cursor = run.getCursorValue();
			Set<String> seen = new HashSet<>();
			int pages = 0;
			int fetched = 0;
			int persisted = 0;
			while (pages++ < MAX_PAGES) {
				ReportPullResult result = "BILLING".equals(type)
						? adapter.pullBilling(account.getId(), start.toString(), end.toString(), cursor)
						: adapter.pullReport(account.getId(), start.toString(), end.toString(), cursor);
				if (result == null)
					throw new IllegalStateException("媒体适配器返回空结果");
				for (ReportPullResult.ReportRow row : result.rows()) {
					fetched++;
					persisted += persist(account, run, type, row);
				}
				String next = result.nextCursor();
				run.setCursorValue(next);
				run.setFetchedRows(fetched);
				run.setPersistedRows(persisted);
				runMapper.updateById(run);
				if (!result.hasMore())
					break;
				if (!StringUtils.hasText(next) || !seen.add(next))
					throw new IllegalStateException("媒体适配器游标未前进");
				cursor = next;
			}
			if (pages > MAX_PAGES)
				throw new IllegalStateException("媒体适配器分页超过安全上限");
			run.setStatus(fetched == 0 ? "SUCCEEDED_EMPTY" : "SUCCEEDED");
			run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			run.setLockedUntil(null);
			runMapper.updateById(run);
			return run;
		}
		catch (RuntimeException exception) {
			run.setStatus("FAILED");
			run.setErrorCode(exception.getClass().getSimpleName());
			run.setErrorMessage(abbreviate(exception.getMessage()));
			run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			run.setLockedUntil(null);
			runMapper.updateById(run);
			throw exception;
		}
	}

	private int persist(MediaAccount account, MediaReportSyncRun run, String type, ReportPullResult.ReportRow row) {
		if (row == null || !StringUtils.hasText(row.externalCampaignId()) || row.dateHour() == null)
			throw new IllegalArgumentException("媒体报表行缺少外部计划或小时");
		LocalDateTime statHourUtc = toUtc(row.dateHour(), zone(account));
		MediaPublishingBinding binding = findBinding(account.getId(), row.externalCampaignId(), statHourUtc);
		String quality = quality(binding, account, row);
		if ("UNBOUND".equals(quality))
			recordIssue(account.getMediaId(), row.dateHour().toLocalDate(), row.externalCampaignId(), "UNBOUND");
		else if ("MISSING_GOAL".equals(quality))
			recordIssue(account.getMediaId(), row.dateHour().toLocalDate(), row.externalCampaignId(), "MISSING_GOAL");
		else if ("CURRENCY_MISMATCH".equals(quality))
			recordIssue(account.getMediaId(), row.dateHour().toLocalDate(), row.externalCampaignId(),
					"CURRENCY_MISMATCH");
		BigDecimal spend = row.spend() == null ? BigDecimal.ZERO : row.spend();
		String currency = StringUtils.hasText(row.currency()) ? row.currency().trim().toUpperCase()
				: (StringUtils.hasText(account.getCurrency()) ? account.getCurrency().trim().toUpperCase() : "CNY");
		MediaReportHourlyFact fact = new MediaReportHourlyFact();
		fact.setId(uuid());
		fact.setSyncRunId(run.getId());
		fact.setSyncType(type);
		fact.setMediaAccountId(account.getId());
		fact.setMediaId(account.getMediaId());
		fact.setChannelId(account.getChannelId());
		fact.setPublishingBindingId(binding == null ? null : binding.getId());
		fact.setCampaignId(binding == null ? null : binding.getCampaignId());
		fact.setTrackingTaskId(binding == null ? null : binding.getTrackingTaskId());
		fact.setExternalCampaignId(row.externalCampaignId());
		fact.setStatHourUtc(statHourUtc);
		fact.setSourceTimezone(zone(account).getId());
		fact.setLocalReportDate(row.dateHour().toLocalDate());
		fact.setLocalHour(row.dateHour().getHour());
		fact.setImpressionCount(row.impressions());
		fact.setClickCount(row.clicks());
		fact.setConversionCount(row.conversions());
		fact.setSpendAmount(spend);
		fact.setCurrency(currency);
		fact.setQualityCode(quality);
		fact.setPulledAt(LocalDateTime.now(ZoneOffset.UTC));
		fact.setSourceHash(SignUtils.sha256(type + "|" + row.externalCampaignId() + "|" + row.dateHour() + "|"
				+ row.impressions() + "|" + row.clicks() + "|" + row.conversions() + "|" + spend));
		MediaReportHourlyFact previous = factMapper.selectLatest(account.getId(), row.externalCampaignId(), statHourUtc,
				type);
		if (previous != null && fact.getSourceHash().equals(previous.getSourceHash()))
			return 0;
		fact.setSourceVersion(previous == null ? 1 : previous.getSourceVersion() + 1);
		factMapper.insert(fact);
		return 1;
	}

	private String quality(MediaPublishingBinding binding, MediaAccount account, ReportPullResult.ReportRow row) {
		if (binding == null)
			return "UNBOUND";
		String accountCurrency = StringUtils.hasText(account.getCurrency()) ? account.getCurrency().trim().toUpperCase()
				: "CNY";
		if (StringUtils.hasText(row.currency()) && !accountCurrency.equals(row.currency().trim().toUpperCase()))
			return "CURRENCY_MISMATCH";
		if (!StringUtils.hasText(binding.getAdGroupId()))
			return "MISSING_GOAL";
		AdGroup adGroup = adGroupMapper.selectById(binding.getAdGroupId());
		if (adGroup == null || !StringUtils.hasText(adGroup.getOptimizationGoalId()))
			return "MISSING_GOAL";
		OptimizationGoal goal = optimizationGoalMapper.selectById(adGroup.getOptimizationGoalId());
		return goal == null || !"ACTIVE".equals(goal.getStatus()) || !StringUtils.hasText(goal.getEventCode())
				? "MISSING_GOAL" : null;
	}

	private MediaPublishingBinding findBinding(String accountId, String externalId, LocalDateTime statHourUtc) {
		return bindingMapper.selectOne(Wrappers.<MediaPublishingBinding>lambdaQuery()
			.eq(MediaPublishingBinding::getMediaAccountId, accountId)
			.eq(MediaPublishingBinding::getExternalCampaignId, externalId)
			.eq(MediaPublishingBinding::getBindingStatus, "ACTIVE")
			.le(MediaPublishingBinding::getEffectiveFrom, statHourUtc)
			.and(q -> q.isNull(MediaPublishingBinding::getEffectiveTo)
				.or()
				.gt(MediaPublishingBinding::getEffectiveTo, statHourUtc))
			.last("LIMIT 1"));
	}

	private void recordIssue(String mediaId, java.time.LocalDate date, String resourceId, String type) {
		if (issueMapper.selectCount(Wrappers.<ReportReconciliationIssue>lambdaQuery()
			.eq(ReportReconciliationIssue::getMediaId, mediaId)
			.eq(ReportReconciliationIssue::getReportDate, date)
			.eq(ReportReconciliationIssue::getIssueType, type)
			.eq(ReportReconciliationIssue::getResourceId, resourceId)
			.eq(ReportReconciliationIssue::getStatus, "OPEN")) > 0)
			return;
		ReportReconciliationIssue issue = new ReportReconciliationIssue();
		issue.setMediaId(mediaId);
		issue.setReportDate(date);
		issue.setIssueType(type);
		issue.setResourceId(resourceId);
		issue.setStatus("OPEN");
		issue.setDetectedAt(LocalDateTime.now(ZoneOffset.UTC));
		issueMapper.insert(issue);
	}

	private MediaAccount requireAccount(String id, boolean write) {
		MediaAccount account = accountMapper.selectById(id);
		if (account == null
				|| (write ? !scopeService.canWrite(account.getMediaId()) : !scopeService.canRead(account.getMediaId())))
			throw new IllegalArgumentException("资源不存在");
		return account;
	}

	private ZoneId zone(MediaAccount account) {
		try {
			return ZoneId.of(StringUtils.hasText(account.getTimezone()) ? account.getTimezone() : "Asia/Shanghai");
		}
		catch (RuntimeException ignored) {
			return ZoneId.of("Asia/Shanghai");
		}
	}

	private LocalDateTime toUtc(LocalDateTime value, ZoneId zone) {
		return value.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private String abbreviate(String message) {
		if (!StringUtils.hasText(message))
			return "未提供错误详情";
		return message.length() <= 512 ? message : message.substring(0, 512);
	}

	private String uuid() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
