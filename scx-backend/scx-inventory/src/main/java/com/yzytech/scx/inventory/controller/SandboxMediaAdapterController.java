package com.yzytech.scx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.inventory.service.MediaAdapterService;
import com.yzytech.scx.inventory.dto.ReportPullResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile({ "dev", "test" })
@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/inventory/adapter/sandbox")
public class SandboxMediaAdapterController {

	private final MediaAdapterService adapterService;

	@PostMapping("/fault-inject")
	@SaCheckPermission("inventory:media-account:write")
	public Result<Boolean> injectFault(@RequestParam String fault, @RequestParam int value) {
		adapterService.injectSandboxFault(fault, value);
		return Result.success(true);
	}

	@PostMapping("/report-page")
	@SaCheckPermission("inventory:media-account:write")
	public Result<Boolean> putReport(@RequestBody SandboxReportPageRequest request) {
		adapterService.putSandboxReport(request.syncType(), request.mediaAccountId(), request.start(), request.end(),
				request.cursor(), new ReportPullResult(request.nextCursor(), request.hasMore(),
						request.rows() == null ? List.of() : request.rows(), LocalDateTime.now()));
		return Result.success(true);
	}

	public record SandboxReportPageRequest(String syncType, String mediaAccountId, String start, String end,
			String cursor, String nextCursor, boolean hasMore, List<ReportPullResult.ReportRow> rows) {
	}

}
