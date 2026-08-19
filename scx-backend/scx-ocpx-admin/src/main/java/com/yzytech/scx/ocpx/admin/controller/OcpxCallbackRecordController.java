package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.CallbackRecordViews;
import com.yzytech.scx.ocpx.admin.service.OcpxCallbackRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/callback-records")
public class OcpxCallbackRecordController {

	private final OcpxCallbackRecordService service;

	@GetMapping
	@SaCheckPermission("ocpx:callback-record:view")
	public Result<CallbackRecordViews.Page> page(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId, String eventCode, Boolean targetConversion,
			String deductionDecision, String requestId, Long current, Long size) {
		return Result.success(service.query(startDate, endDate, advertiserId, promotionLinkId, mediaId, monitorTaskId,
				eventCode, targetConversion, deductionDecision, requestId, current, size));
	}

}
