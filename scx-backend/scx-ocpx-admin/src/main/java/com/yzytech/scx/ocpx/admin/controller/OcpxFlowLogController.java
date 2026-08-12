package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import com.yzytech.scx.ocpx.admin.service.OcpxFlowLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/flow-logs")
public class OcpxFlowLogController {

	private final OcpxFlowLogService service;

	@GetMapping
	@SaCheckPermission("ocpx:flow-log:view")
	public Result<FlowLogViews.Page> page(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String flowType,
			String resultStatus, String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId,
			String eventId, Long current, Long size) {
		return Result.success(service.query(startDate, endDate, flowType, resultStatus, advertiserId,
				promotionLinkId, mediaId, monitorTaskId, eventId, current, size));
	}

}
