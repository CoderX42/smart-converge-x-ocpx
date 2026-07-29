package com.yzytech.scx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.inventory.dto.*;
import com.yzytech.scx.inventory.service.MediaAdapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/inventory/adapter")
@Tag(name = "媒体适配器", description = "媒体Adapter操作与健康检查")
public class MediaAdapterController {

	private final MediaAdapterService adapterService;

	@GetMapping("/{mediaAccountId}/capabilities")
	@SaCheckPermission("inventory:media-account:read")
	public Result<CapabilityView> capabilities(@PathVariable String mediaAccountId) {
		return call(() -> adapterService.getCapabilities(mediaAccountId));
	}

	@PostMapping("/{mediaAccountId}/publish")
	@SaCheckPermission("inventory:media-account:write")
	@Operation(summary = "发布计划到外部媒体")
	public Result<PublishResult> publish(@PathVariable String mediaAccountId, @RequestBody PublishRequest req) {
		return call(() -> adapterService.publishCampaign(mediaAccountId, req));
	}

	@PostMapping("/{mediaAccountId}/change-status")
	@SaCheckPermission("inventory:media-account:write")
	public Result<PublishResult> changeStatus(@PathVariable String mediaAccountId, @RequestParam String externalId,
			@RequestParam String newStatus, @RequestParam String idempotencyKey) {
		return call(() -> adapterService.changeStatus(mediaAccountId, externalId, newStatus, idempotencyKey));
	}

	@GetMapping("/{mediaAccountId}/status")
	@SaCheckPermission("inventory:media-account:read")
	public Result<StatusQueryResult> queryStatus(@PathVariable String mediaAccountId, @RequestParam String externalId) {
		return call(() -> adapterService.queryStatus(mediaAccountId, externalId));
	}

	@GetMapping("/{mediaAccountId}/report")
	@SaCheckPermission("inventory:media-account:read")
	public Result<ReportPullResult> pullReport(@PathVariable String mediaAccountId, @RequestParam String start,
			@RequestParam String end, @RequestParam(required = false) String cursor) {
		return call(() -> adapterService.pullReport(mediaAccountId, start, end, cursor));
	}

	@GetMapping("/{mediaAccountId}/health")
	@SaCheckPermission("inventory:media-account:read")
	public Result<Boolean> health(@PathVariable String mediaAccountId) {
		return call(() -> adapterService.isHealthy(mediaAccountId));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败: " + e.getMessage());
		}
	}

}
