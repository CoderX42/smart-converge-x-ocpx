package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.dto.CatalogViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxDeductionRule;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.service.AdxCatalogService;
import com.yzytech.scx.adx.service.AdxAdapterService;
import com.yzytech.scx.adx.service.AdxRoutePublishService;
import com.yzytech.scx.common.core.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/adx")
public class AdxCatalogController {

	private final AdxCatalogService service;

	private final AdxAdapterService adapterService;

	private final AdxRoutePublishService routePublishService;

	@GetMapping("/adapters")
	@SaCheckPermission("adx:monitor:read")
	public Result<List<CatalogViews.Adapter>> adapters() {
		return Result.success(adapterService.list());
	}

	@GetMapping("/report-options")
	@SaCheckPermission("adx:report:read")
	public Result<Map<String, Object>> reportOptions() {
		return Result.success(service.reportOptions());
	}

	@GetMapping("/channels")
	@SaCheckPermission("adx:channel:read")
	public Result<IPage<CatalogViews.Channel>> channels(Page<AdxChannel> page, String keyword) {
		return Result.success(service.channels(page, keyword));
	}

	@PostMapping("/channels")
	@SaCheckPermission("adx:channel:write")
	public Result<CatalogViews.Channel> createChannel(@Valid @RequestBody CatalogRequests.SaveChannel request) {
		return call(() -> service.saveChannel(null, request));
	}

	@PutMapping("/channels/{id}")
	@SaCheckPermission("adx:channel:write")
	public Result<CatalogViews.Channel> updateChannel(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveChannel request) {
		return call(() -> service.saveChannel(id, request));
	}

	@DeleteMapping("/channels/{id}")
	@SaCheckPermission("adx:channel:write")
	public Result<Boolean> deleteChannel(@PathVariable String id) {
		return call(() -> {
			service.deleteChannel(id);
			return true;
		});
	}

	@GetMapping("/advertisers")
	@SaCheckPermission("adx:advertiser:read")
	public Result<IPage<CatalogViews.Advertiser>> advertisers(Page<AdxAdvertiser> page, String keyword) {
		return Result.success(service.advertisers(page, keyword));
	}

	@PostMapping("/advertisers")
	@SaCheckPermission("adx:advertiser:write")
	public Result<CatalogViews.Advertiser> createAdvertiser(
			@Valid @RequestBody CatalogRequests.SaveAdvertiser request) {
		return call(() -> service.saveAdvertiser(null, request));
	}

	@PutMapping("/advertisers/{id}")
	@SaCheckPermission("adx:advertiser:write")
	public Result<CatalogViews.Advertiser> updateAdvertiser(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveAdvertiser request) {
		return call(() -> service.saveAdvertiser(id, request));
	}

	@PostMapping("/advertisers/{id}/recharge")
	@SaCheckPermission("adx:advertiser:write")
	public Result<CatalogViews.Advertiser> recharge(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.Recharge request) {
		return call(() -> service.recharge(id, request.amount()));
	}

	@DeleteMapping("/advertisers/{id}")
	@SaCheckPermission("adx:advertiser:write")
	public Result<Boolean> deleteAdvertiser(@PathVariable String id) {
		return call(() -> {
			service.deleteAdvertiser(id);
			return true;
		});
	}

	@GetMapping("/app-links")
	@SaCheckPermission("adx:app-link:read")
	public Result<IPage<CatalogViews.AppLink>> appLinks(Page<AdxAppLink> page, String advertiserId, String keyword) {
		return Result.success(service.appLinks(page, advertiserId, keyword));
	}

	@PostMapping("/app-links")
	@SaCheckPermission("adx:app-link:write")
	public Result<CatalogViews.AppLink> createAppLink(@Valid @RequestBody CatalogRequests.SaveAppLink request) {
		return call(() -> service.saveAppLink(null, request));
	}

	@PutMapping("/app-links/{id}")
	@SaCheckPermission("adx:app-link:write")
	public Result<CatalogViews.AppLink> updateAppLink(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveAppLink request) {
		return call(() -> service.saveAppLink(id, request));
	}

	@DeleteMapping("/app-links/{id}")
	@SaCheckPermission("adx:app-link:write")
	public Result<Boolean> deleteAppLink(@PathVariable String id) {
		return call(() -> {
			service.deleteAppLink(id);
			return true;
		});
	}

	@GetMapping("/monitors")
	@SaCheckPermission("adx:monitor:read")
	public Result<IPage<CatalogViews.Monitor>> monitors(Page<AdxMonitorLink> page, String channelId,
			String advertiserId, String keyword) {
		return Result.success(service.monitors(page, channelId, advertiserId, keyword));
	}

	@PostMapping("/monitors")
	@SaCheckPermission("adx:monitor:write")
	public Result<CatalogViews.Monitor> createMonitor(@Valid @RequestBody CatalogRequests.SaveMonitor request) {
		return call(() -> service.saveMonitor(null, request));
	}

	@PutMapping("/monitors/{id}")
	@SaCheckPermission("adx:monitor:write")
	public Result<CatalogViews.Monitor> updateMonitor(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveMonitor request) {
		return call(() -> service.saveMonitor(id, request));
	}

	@DeleteMapping("/monitors/{id}")
	@SaCheckPermission("adx:monitor:write")
	public Result<Boolean> deleteMonitor(@PathVariable String id) {
		return call(() -> {
			service.deleteMonitor(id);
			return true;
		});
	}

	@PostMapping("/monitors/{id}/publish")
	@SaCheckPermission("adx:monitor:publish")
	public Result<Map<String, Object>> publishMonitor(@PathVariable String id) {
		return call(() -> routePublishService.publish(id));
	}

	@GetMapping("/deduction-rules")
	@SaCheckPermission("adx:deduction:read")
	public Result<IPage<CatalogViews.Deduction>> deductions(Page<AdxDeductionRule> page, String channelId,
			String appLinkId) {
		return Result.success(service.deductions(page, channelId, appLinkId));
	}

	@PostMapping("/deduction-rules")
	@SaCheckPermission("adx:deduction:write")
	public Result<CatalogViews.Deduction> createDeduction(@Valid @RequestBody CatalogRequests.SaveDeduction request) {
		return call(() -> service.saveDeduction(null, request));
	}

	@PutMapping("/deduction-rules/{id}")
	@SaCheckPermission("adx:deduction:write")
	public Result<CatalogViews.Deduction> updateDeduction(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveDeduction request) {
		return call(() -> service.saveDeduction(id, request));
	}

	@DeleteMapping("/deduction-rules/{id}")
	@SaCheckPermission("adx:deduction:write")
	public Result<Boolean> deleteDeduction(@PathVariable String id) {
		return call(() -> {
			service.deleteDeduction(id);
			return true;
		});
	}

	private <T> Result<T> call(Callable<T> action) {
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
