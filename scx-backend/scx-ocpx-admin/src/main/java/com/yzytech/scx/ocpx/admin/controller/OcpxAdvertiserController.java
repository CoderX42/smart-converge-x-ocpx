package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.service.OcpxAdvertiserService;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.service.AdvertiserRolePermissionManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/advertisers")
public class OcpxAdvertiserController {

	private final OcpxAdvertiserService advertiserService;

	private final AdvertiserRolePermissionManager rolePermissionManager;

	@GetMapping
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<IPage<AdvertiserViews.Item>> list(Page<OcpxAdvertiser> page, String keyword, String advertiserId) {
		return Result.success(advertiserService.list(page, keyword, advertiserId));
	}

	@GetMapping("/options")
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<List<AdvertiserViews.Option>> options() {
		return Result.success(advertiserService.options());
	}

	@GetMapping("/account-role/permissions")
	@SaCheckPermission(SystemAccountConstants.ADVERTISER_ROLE_MANAGE_PERMISSION)
	public Result<AdvertiserRolePermissionManager.Configuration> accountRolePermissions() {
		return Result.success(rolePermissionManager.getConfiguration());
	}

	@PutMapping("/account-role/permissions")
	@SaCheckPermission(SystemAccountConstants.ADVERTISER_ROLE_MANAGE_PERMISSION)
	@SysLog("修改广告主账号角色权限")
	public Result<AdvertiserRolePermissionManager.Configuration> updateAccountRolePermissions(
			@Valid @RequestBody AdvertiserRequests.RolePermissionsUpdate request) {
		return Result.success(
				rolePermissionManager.updateConfiguration(request.getMenuIds(), request.getAllDataScope().booleanValue()));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<AdvertiserViews.Item> detail(@PathVariable String id) {
		return Result.success(advertiserService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:advertiser:add")
	@SysLog("新增广告主")
	public Result<AdvertiserViews.Item> create(@Valid @RequestBody AdvertiserRequests.Create request) {
		return Result.success(advertiserService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:edit")
	@SysLog("修改广告主")
	public Result<AdvertiserViews.Item> update(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.Update request) {
		return Result.success(advertiserService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:delete")
	@SysLog("删除广告主")
	public Result<Void> delete(@PathVariable String id) {
		advertiserService.delete(id);
		return Result.success();
	}

	@PostMapping("/{id}/recharge")
	@SaCheckPermission("ocpx:advertiser:recharge")
	@SysLog("广告主充值")
	public Result<AdvertiserViews.RechargeResult> recharge(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.Recharge request) {
		return Result.success(advertiserService.recharge(id, request));
	}

	@PostMapping("/{id}/deduct")
	@SaCheckPermission(SystemAccountConstants.ADVERTISER_DEDUCT_PERMISSION)
	@SysLog("扣减广告主余额")
	public Result<AdvertiserViews.DeductionResult> deduct(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.Deduction request) {
		return Result.success(advertiserService.deduct(id, request));
	}

}
