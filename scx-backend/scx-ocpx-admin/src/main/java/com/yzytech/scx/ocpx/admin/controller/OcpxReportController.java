package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.dto.DashboardOverviewViews;
import com.yzytech.scx.ocpx.admin.dto.DashboardQuery;
import com.yzytech.scx.ocpx.admin.dto.DashboardViews;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.service.OcpxReportService;
import com.yzytech.scx.ocpx.admin.service.OcpxReportExportService;
import com.yzytech.scx.ocpx.admin.service.OcpxDashboardService;
import com.yzytech.scx.ocpx.admin.service.OcpxUnifiedReportService;
import com.yzytech.scx.common.core.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx")
public class OcpxReportController {

	private final OcpxReportService service;

	private final OcpxDashboardService dashboardService;

	private final OcpxUnifiedReportService unifiedReportService;

	private final OcpxReportExportService exportService;

	@GetMapping("/dashboard/summary")
	@SaCheckPermission("ocpx:dashboard:view")
	public Result<DashboardOverviewViews.Overview> dashboard(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String advertiserId, String productId,
			String appLinkId, String taskId, String channelId, String mediaAccountId) {
		try {
			return Result.success(dashboardService.overview(new DashboardQuery(startDate, endDate, advertiserId,
					productId, appLinkId, taskId, channelId, mediaAccountId)));
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/dashboard/options")
	@SaCheckPermission("ocpx:dashboard:view")
	public Result<UnifiedReportViews.Options> dashboardOptions() {
		return Result.success(dashboardService.options());
	}

	@GetMapping("/dashboard/account-summary")
	@SaCheckPermission("ocpx:account:read")
	public Result<DashboardViews.AccountSummary> accountSummary(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String channelId, String advertiserId,
			String appLinkId, String monitorId) {
		try {
			return Result
				.success(service.accountSummary(startDate, endDate, channelId, advertiserId, appLinkId, monitorId));
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/reports/hourly")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.Page> summary(String grain, String groupBy, String mediaLevel, String channelId,
			String advertiserId, String productId, String appLinkId, String taskId, String mediaAccountId,
			java.util.List<String> metrics, String sort, String direction, Long current, Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		try {
			return Result.success(unifiedReportService.query(new UnifiedReportQuery(startDate, endDate, grain, groupBy,
					mediaLevel, advertiserId, productId, appLinkId, taskId, channelId, mediaAccountId, metrics, sort,
					direction, current == null ? 1 : current, size == null ? 20 : size)));
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/reports/options")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.Options> options() {
		return Result.success(unifiedReportService.options());
	}

	@PostMapping("/reports/exports")
	@SaCheckPermission("ocpx:report:export")
	public Result<UnifiedReportViews.ExportTask> createExport(@RequestBody UnifiedReportQuery query) {
		return Result.success(exportService.createStandard(query));
	}

	@PostMapping("/reports/deduction-exports")
	@SaCheckPermission("ocpx:report:deduction:export")
	public Result<UnifiedReportViews.ExportTask> createDeductionExport(@RequestBody UnifiedReportQuery query) {
		return Result.success(exportService.createDeduction(query));
	}

	@GetMapping("/reports/exports/{id}")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.ExportTask> exportDetail(@PathVariable String id) {
		return Result.success(exportService.detail(id));
	}

	@PostMapping("/reports/exports/{id}/cancel")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.ExportTask> cancelExport(@PathVariable String id) {
		return Result.success(exportService.cancel(id));
	}

	@GetMapping("/reports/exports/{id}/download")
	@SaCheckPermission("ocpx:report:view")
	public ResponseEntity<InputStreamResource> downloadExport(@PathVariable String id) {
		OcpxReportExportService.ExportDownload download = exportService.download(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.size())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(download.fileName(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.body(new InputStreamResource(download.inputStream()));
	}

}
