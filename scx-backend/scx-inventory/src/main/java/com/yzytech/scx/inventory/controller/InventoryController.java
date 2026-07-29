package com.yzytech.scx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.inventory.entity.*;
import com.yzytech.scx.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/inventory")
@Tag(name = "媒体库存", description = "媒体账户、广告位与能力矩阵")
public class InventoryController {

	private final InventoryService service;

	@PostMapping("/media-accounts")
	@SaCheckPermission("inventory:media-account:write")
	@Operation(summary = "创建媒体账户")
	public Result<MediaAccount> createAccount(@RequestParam String mediaId, @RequestParam String accountName,
			@RequestParam(required = false) String adapterType,
			@RequestParam(required = false) String accountIdExternal) {
		return call(() -> service.createMediaAccount(mediaId, accountName, adapterType, accountIdExternal));
	}

	@GetMapping("/media-accounts")
	@SaCheckPermission("inventory:media-account:read")
	@Operation(summary = "查询媒体账户列表")
	public Result<Page<MediaAccount>> listAccounts(Page<MediaAccount> page, @RequestParam String mediaId,
			String keyword) {
		return call(() -> service.listMediaAccounts(page, mediaId, keyword));
	}

	@PutMapping("/media-accounts/{id}")
	@SaCheckPermission("inventory:media-account:write")
	@Operation(summary = "编辑媒体账户")
	public Result<MediaAccount> updateAccount(@PathVariable String id, @RequestParam String accountName,
			@RequestParam(required = false) String accountIdExternal,
			@RequestParam(required = false) String adapterType) {
		return call(() -> service.updateMediaAccount(id, accountName, accountIdExternal, adapterType));
	}

	@PutMapping("/media-accounts/{id}/status")
	@SaCheckPermission("inventory:media-account:write")
	@Operation(summary = "启停媒体账户")
	public Result<MediaAccount> updateAccountStatus(@PathVariable String id, @RequestParam String status) {
		return call(() -> service.updateMediaAccountStatus(id, status));
	}

	@PostMapping("/placements")
	@SaCheckPermission("inventory:placement:write")
	@Operation(summary = "创建广告位")
	public Result<Placement> createPlacement(@RequestParam String mediaAccountId, @RequestParam String code,
			@RequestParam String name, @RequestParam String type, @RequestParam(required = false) String creatives) {
		return call(() -> service.createPlacement(mediaAccountId, code, name, type, creatives));
	}

	@GetMapping("/placements")
	@SaCheckPermission("inventory:placement:read")
	@Operation(summary = "查询广告位列表")
	public Result<Page<Placement>> listPlacements(Page<Placement> page, String mediaAccountId, String type) {
		return call(() -> service.listPlacements(page, mediaAccountId, type));
	}

	@PutMapping("/placements/{id}")
	@SaCheckPermission("inventory:placement:write")
	@Operation(summary = "编辑广告位")
	public Result<Placement> updatePlacement(@PathVariable String id, @RequestParam String name,
			@RequestParam(required = false) String type, @RequestParam(required = false) String creatives) {
		return call(() -> service.updatePlacement(id, name, type, creatives));
	}

	@PutMapping("/placements/{id}/status")
	@SaCheckPermission("inventory:placement:write")
	@Operation(summary = "启停广告位")
	public Result<Placement> updatePlacementStatus(@PathVariable String id, @RequestParam String status) {
		return call(() -> service.updatePlacementStatus(id, status));
	}

	@GetMapping("/placements/{id}/capabilities")
	@SaCheckPermission("inventory:placement:read")
	@Operation(summary = "查询广告位能力矩阵")
	public Result<MediaCapability> capabilities(@PathVariable String id) {
		return call(() -> service.getCapability(id));
	}

	@PutMapping("/placements/{id}/capabilities")
	@SaCheckPermission("inventory:capability:write")
	@Operation(summary = "维护广告位能力矩阵")
	public Result<MediaCapability> updateCapabilities(@PathVariable String id,
			@RequestParam(required = false) String billingModes, @RequestParam(required = false) String targetEvents,
			@RequestParam(required = false) String targetingFields,
			@RequestParam(required = false) String creativeFormats,
			@RequestParam(required = false) Boolean mediaManagedOcpc,
			@RequestParam(required = false) Boolean statusSyncSupported,
			@RequestParam(required = false) Boolean reportPullSupported) {
		return call(() -> service.updateCapability(id, billingModes, targetEvents, targetingFields, creativeFormats,
				mediaManagedOcpc, statusSyncSupported, reportPullSupported));
	}

	@GetMapping("/placements/{id}/quality")
	@SaCheckPermission("inventory:placement:read")
	@Operation(summary = "查询广告位质量快照")
	public Result<PlacementQualitySnapshot> quality(@PathVariable String id) {
		return call(() -> service.getLatestQuality(id));
	}

	@GetMapping("/placements/{id}/quality/page")
	@SaCheckPermission("inventory:placement:read")
	@Operation(summary = "查询广告位质量快照历史")
	public Result<Page<PlacementQualitySnapshot>> qualityPage(@PathVariable String id,
			Page<PlacementQualitySnapshot> page) {
		return call(() -> service.listQuality(page, id));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
