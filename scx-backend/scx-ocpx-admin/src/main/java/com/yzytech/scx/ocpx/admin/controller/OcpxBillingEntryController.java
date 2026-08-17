package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.BillingEntryViews;
import com.yzytech.scx.ocpx.admin.service.OcpxBillingEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/billing-entries")
public class OcpxBillingEntryController {

	private final OcpxBillingEntryService service;

	@GetMapping
	@SaCheckPermission("ocpx:billing-entry:view")
	public Result<Page<BillingEntryViews.Item>> list(Long current, Long size, String advertiserId, String monitorTaskId,
			String entryType, String status, String businessKey,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
		return Result.success(service.list(current == null ? 1 : current, size == null ? 30 : size, advertiserId,
				monitorTaskId, entryType, status, businessKey, startTime, endTime));
	}

}
