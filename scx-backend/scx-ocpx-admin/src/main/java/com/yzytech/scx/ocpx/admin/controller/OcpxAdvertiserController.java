package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.service.OcpxAdvertiserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/advertisers")
public class OcpxAdvertiserController {

	private final OcpxAdvertiserService advertiserService;

	@GetMapping
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<IPage<AdvertiserViews.Item>> list(Page<OcpxAdvertiser> page, String keyword, String adapterCode,
			String status) {
		return Result.success(advertiserService.list(page, keyword, adapterCode, status));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<AdvertiserViews.Item> detail(@PathVariable String id) {
		return call(() -> advertiserService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:advertiser:add")
	@SysLog("新增广告主")
	public Result<AdvertiserViews.Item> create(@Valid @RequestBody AdvertiserRequests.Create request) {
		return call(() -> advertiserService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:edit")
	@SysLog("修改广告主")
	public Result<AdvertiserViews.Item> update(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.Update request) {
		return call(() -> advertiserService.update(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:advertiser:status")
	@SysLog("修改广告主状态")
	public Result<AdvertiserViews.Mutation> status(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.ChangeStatus request) {
		return call(() -> advertiserService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:advertiser:archive")
	@SysLog("归档广告主")
	public Result<AdvertiserViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.VersionedAction request) {
		return call(() -> advertiserService.archive(id, request));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		catch (BootBusinessException error) {
			throw error;
		}
		catch (Exception error) {
			return Result.fail("操作失败");
		}
	}

}
