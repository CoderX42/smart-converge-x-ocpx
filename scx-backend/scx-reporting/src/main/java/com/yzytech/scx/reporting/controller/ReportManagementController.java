package com.yzytech.scx.reporting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.reporting.dto.CreateBackfillRequest;
import com.yzytech.scx.reporting.entity.MediaReportSyncRun;
import com.yzytech.scx.reporting.entity.ReportBackfill;
import com.yzytech.scx.reporting.service.MediaReportSyncService;
import com.yzytech.scx.reporting.service.ReportBackfillService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/reports")
public class ReportManagementController {

	private final MediaReportSyncService syncService;

	private final ReportBackfillService backfillService;

	@GetMapping("/sync-runs")
	@SaCheckPermission("ad:report:read")
	public Result<Page<MediaReportSyncRun>> syncRuns(Page<MediaReportSyncRun> page, @RequestParam String mediaAccountId,
			String status) {
		return call(() -> syncService.page(page, mediaAccountId, status));
	}

	@PostMapping("/media-accounts/{id}/sync")
	@SaCheckPermission("ad:report:sync")
	public Result<List<MediaReportSyncRun>> sync(@PathVariable String id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
		return call(() -> syncService.sync(id, start, end));
	}

	@PostMapping("/backfills")
	@SaCheckPermission("ad:report:backfill")
	public Result<ReportBackfill> createBackfill(@RequestBody CreateBackfillRequest request) {
		return call(() -> backfillService.create(request));
	}

	@GetMapping("/backfills/{id}")
	@SaCheckPermission("ad:report:backfill")
	public Result<ReportBackfill> backfill(@PathVariable String id) {
		return call(() -> backfillService.get(id));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException exception) {
			return Result.fail(exception.getMessage());
		}
		catch (Exception exception) {
			log.error("报表管理操作失败", exception);
			return Result.fail("报表管理操作失败");
		}
	}

}
