package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.adx.dto.RuntimeRequests;
import com.yzytech.scx.adx.dto.RuntimeViews;
import com.yzytech.scx.adx.service.AdxRuntimeService;
import com.yzytech.scx.common.core.util.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** Fixed-Task projections used by the three Task detail drawers. */
@RestController
@RequestMapping("/adx/tasks")
public class AdxTaskRuntimeController {

	private final AdxRuntimeService service;

	public AdxTaskRuntimeController(AdxRuntimeService service) {
		this.service = service;
	}

	@GetMapping("/{id}/integration-facts")
	@SaCheckPermission("adx:runtime:view")
	public Result<RuntimeViews.PageResult<RuntimeViews.Fact>> integrationFacts(@PathVariable String id,
			@RequestParam(required = false) Long current, @RequestParam(required = false) Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime, String status, String exactId,
			String traceId, String sort) {
		RuntimeRequests.Query query = new RuntimeRequests.Query(current, size, startTime, endTime, "INTEGRATION", null,
				status, null, null, null, null, id, null, null, null, null, exactId, traceId, sort);
		return Result.success(service.taskFacts(id, "INTEGRATION", query));
	}

}
