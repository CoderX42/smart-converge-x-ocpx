package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportViews;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.service.OcpxUnifiedReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Slf4j
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

	@GetMapping("/reports/summary")
	@SaCheckPermission("ocpx:report:view")
	public Result<SummaryReportViews.Page> summary(Integer dateType, String mediaId, String monitorTaskId,
			String advertiserId, String promotionLinkId, String startTime, String endTime, String asc, String desc,
			Long current, Long size) {
		return Result.success(service.summary(new SummaryReportQuery(dateType, mediaId, monitorTaskId, advertiserId,
				promotionLinkId, startTime, endTime, asc, desc, current, size)));
	}

	@GetMapping("/reports/summary:export")
	@SaCheckPermission("ocpx:report:view")
	public void exportSummary(Integer dateType, String mediaId, String monitorTaskId, String advertiserId,
			String promotionLinkId, String startTime, String endTime, HttpServletResponse response) {
		try {
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.setContentType("text/csv;charset=UTF-8");
			String fileName = URLEncoder.encode("时段报表", StandardCharsets.UTF_8).replace("+", "%20");
			response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".csv");
			response.getWriter()
				.print("\ufeff"
						+ service.summaryCsv(new SummaryReportQuery(dateType, mediaId, monitorTaskId, advertiserId,
								promotionLinkId, startTime, endTime, null, null, null, null)));
		}
		catch (Exception error) {
			log.error("Export csv error!!!", error);
		}
	}

	private UnifiedReportQuery query(LocalDate startDate, LocalDate endDate, String groupBy, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId, Long current, Long size) {
		return new UnifiedReportQuery(startDate, endDate, groupBy, advertiserId, promotionLinkId, mediaId,
				monitorTaskId, current == null ? 1 : current, size == null ? 30 : size);
	}

}
