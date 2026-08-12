package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.service.OcpxUnifiedReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx")
public class OcpxReportController {

	private final OcpxUnifiedReportService service;

	@GetMapping("/dashboard/summary")
	@SaCheckPermission("ocpx:dashboard:view")
	public Result<UnifiedReportViews.Dashboard> dashboard(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId) {
		return Result.success(service.dashboard(query(startDate, endDate, "TIME", advertiserId, promotionLinkId,
				mediaId, monitorTaskId, 1L, 20L)));
	}

	@GetMapping("/dashboard/options")
	@SaCheckPermission("ocpx:dashboard:view")
	public Result<UnifiedReportViews.Options> dashboardOptions() {
		return Result.success(service.options());
	}

	@GetMapping("/reports/options")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.Options> reportOptions() {
		return Result.success(service.options());
	}

	@GetMapping("/reports/hourly")
	@SaCheckPermission("ocpx:report:view")
	public Result<UnifiedReportViews.Page> report(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String groupBy, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId, Long current, Long size) {
		return Result.success(service.query(query(startDate, endDate, groupBy, advertiserId, promotionLinkId, mediaId,
				monitorTaskId, current, size)));
	}

	private UnifiedReportQuery query(LocalDate startDate, LocalDate endDate, String groupBy, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId, Long current, Long size) {
		return new UnifiedReportQuery(startDate, endDate, groupBy, advertiserId, promotionLinkId, mediaId,
				monitorTaskId, current == null ? 1 : current, size == null ? 20 : size);
	}

}
