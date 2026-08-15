package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.service.OcpxPromotionLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/ocpx/promotion-links")
public class OcpxPromotionLinkController {

	private final OcpxPromotionLinkService promotionLinkService;

	@GetMapping
	@SaCheckPermission("ocpx:promotion-link:view")
	public Result<IPage<PromotionLinkViews.Item>> list(Page<OcpxPromotionLink> page, String keyword,
			String advertiserId, String promotionLinkId, Integer platform) {
		return Result.success(promotionLinkService.list(page, keyword, advertiserId, promotionLinkId, platform));
	}

	@GetMapping("/advertiser-options")
	@SaCheckPermission(value = { "ocpx:promotion-link:view", "ocpx:monitor-task:view" }, mode = SaMode.OR)
	public Result<List<PromotionLinkViews.AdvertiserOption>> advertiserOptions(String keyword) {
		return Result.success(promotionLinkService.advertiserOptions(keyword));
	}

	@GetMapping("/options")
	@SaCheckPermission(
			value = { "ocpx:promotion-link:view", "ocpx:monitor-task:view", "ocpx:deduction-rule:list",
					"ocpx:report:view" },
			mode = SaMode.OR)
	public Result<List<PromotionLinkViews.Option>> options(String advertiserId) {
		return Result.success(promotionLinkService.options(advertiserId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:promotion-link:view")
	public Result<PromotionLinkViews.Item> detail(@PathVariable String id) {
		return Result.success(promotionLinkService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:promotion-link:add")
	@SysLog("新增推广链接")
	public Result<PromotionLinkViews.Item> create(@Valid @RequestBody PromotionLinkRequests.Create request) {
		return Result.success(promotionLinkService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:promotion-link:edit")
	@SysLog("修改推广链接")
	public Result<PromotionLinkViews.Item> update(@PathVariable String id,
			@Valid @RequestBody PromotionLinkRequests.Update request) {
		return Result.success(promotionLinkService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:promotion-link:delete")
	@SysLog("删除推广链接")
	public Result<Void> delete(@PathVariable String id) {
		promotionLinkService.delete(id);
		return Result.success();
	}

}
