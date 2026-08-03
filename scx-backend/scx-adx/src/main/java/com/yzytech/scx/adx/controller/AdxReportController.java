package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.service.AdxReportService;
import com.yzytech.scx.common.core.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/adx")
public class AdxReportController {

	private final AdxReportService service;

	@GetMapping("/dashboard/summary")
	@SaCheckPermission("adx:dashboard:read")
	public Result<Map<String, Object>> dashboard() {
		return Result.success(service.dashboard());
	}

	@GetMapping("/reports/hourly")
	@SaCheckPermission("adx:report:read")
	public Result<Page<ReportRow>> summary(Page<ReportRow> page, String granularity, String channelId, String monitorId,
			String advertiserId, String appLinkId, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		try {
			return Result.success(service.summary(page, granularity, channelId, monitorId, advertiserId, appLinkId,
					startDate, endDate));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/reports/hourly/export")
	@SaCheckPermission("adx:report:export")
	public ResponseEntity<byte[]> export(String granularity, String channelId, String monitorId, String advertiserId,
			String appLinkId, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws java.io.IOException {
		byte[] body = service.export(granularity, channelId, monitorId, advertiserId, appLinkId, startDate, endDate);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename("adx-report.xlsx").build());
		return ResponseEntity.ok()
			.headers(headers)
			.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.body(body);
	}

}
