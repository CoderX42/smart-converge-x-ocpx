package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.service.OcpxPromotionLinkService;
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

import java.util.List;
import java.util.concurrent.Callable;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/promotion-links")
public class OcpxPromotionLinkController {

	private final OcpxPromotionLinkService promotionLinkService;

	@GetMapping
	@SaCheckPermission("ocpx:promotion-link:view")
	public Result<IPage<PromotionLinkViews.Item>> list(Page<OcpxPromotionLink> page, String keyword,
			String advertiserId, String platform, String status) {
		return Result.success(promotionLinkService.list(page, keyword, advertiserId, platform, status));
	}

	@GetMapping("/advertiser-options")
	@SaCheckPermission("ocpx:promotion-link:view")
	public Result<List<PromotionLinkViews.AdvertiserOption>> advertiserOptions(String keyword) {
		return Result.success(promotionLinkService.advertiserOptions(keyword));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:promotion-link:view")
	public Result<PromotionLinkViews.Item> detail(@PathVariable String id) {
		return call(() -> promotionLinkService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:promotion-link:add")
	@SysLog("新增推广链接")
	public Result<PromotionLinkViews.Item> create(@Valid @RequestBody PromotionLinkRequests.Create request) {
		return call(() -> promotionLinkService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:promotion-link:edit")
	@SysLog("修改推广链接")
	public Result<PromotionLinkViews.Item> update(@PathVariable String id,
			@Valid @RequestBody PromotionLinkRequests.Update request) {
		return call(() -> promotionLinkService.update(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:promotion-link:status")
	@SysLog("修改推广链接状态")
	public Result<PromotionLinkViews.Mutation> status(@PathVariable String id,
			@Valid @RequestBody PromotionLinkRequests.StatusAction request) {
		return call(() -> promotionLinkService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:promotion-link:archive")
	@SysLog("归档推广链接")
	public Result<PromotionLinkViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody PromotionLinkRequests.VersionedAction request) {
		return call(() -> promotionLinkService.archive(id, request));
	}

	@PostMapping("/url:validate")
	@SaCheckPermission("ocpx:promotion-link:validate")
	public Result<PromotionLinkViews.ValidationReport> validateUrl(
			@Valid @RequestBody PromotionLinkRequests.ValidateUrl request) {
		return call(() -> promotionLinkService.validateUrl(request));
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
