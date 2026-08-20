package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import com.yzytech.scx.ocpx.admin.service.OcpxFlowLogService;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
	public Result<FlowLogViews.Page> page(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String flowType, String resultStatus,
			String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId, String eventId,
			String stageCode, Long current, Long size) {
		return Result.success(service.query(startDate, endDate, flowType, resultStatus, advertiserId, promotionLinkId,
				mediaId, monitorTaskId, eventId, stageCode, current, size));
	}

	@GetMapping("/{id}/request-detail")
	@SaCheckPermission(SystemAccountConstants.FLOW_LOG_DETAIL_PERMISSION)
	public ResponseEntity<Result<FlowLogViews.RequestDetail>> requestDetail(@PathVariable String id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(service.requestDetail(id)));
	}

}
