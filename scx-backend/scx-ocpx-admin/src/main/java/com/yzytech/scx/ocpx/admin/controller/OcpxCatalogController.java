package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yzytech.scx.ocpx.admin.dto.CatalogRequests;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.service.OcpxCatalogService;
import com.yzytech.scx.ocpx.admin.service.OcpxAdapterService;
import com.yzytech.scx.ocpx.admin.service.OcpxRoutePublishService;
import com.yzytech.scx.ocpx.admin.service.OcpxIntegrationService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.security.handler.BootBusinessException;
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
@RequestMapping("/ocpx")
public class OcpxCatalogController {

	private final OcpxCatalogService service;

	private final OcpxAdapterService adapterService;

	private final OcpxRoutePublishService routePublishService;

	private final OcpxIntegrationService integrationService;

	@GetMapping("/adapters")
	@SaCheckPermission("ocpx:monitor:read")
	public Result<List<CatalogViews.Adapter>> adapters() {
		return Result.success(adapterService.list());
	}

	/**
	 * 获取一个监测链接的对接信息。响应中只有占位符和签名公式，不会返回渠道密钥。
	 */
	@GetMapping("/monitors/{id}/integration")
	@SaCheckPermission("ocpx:monitor:read")
	public Result<CatalogViews.OcpxIntegration> monitorIntegration(@PathVariable String id) {
		return call(() -> integrationService.get(id));
	}

	@GetMapping("/report-options")
	@SaCheckPermission("ocpx:report:read")
	public Result<Map<String, Object>> reportOptions() {
		return Result.success(service.reportOptions());
	}

	@GetMapping("/channels")
	@SaCheckPermission("ocpx:channel:read")
	public Result<IPage<CatalogViews.Channel>> channels(Page<OcpxChannel> page, String keyword) {
		return Result.success(service.channels(page, keyword));
	}

	@PostMapping("/channels")
	@SaCheckPermission("ocpx:channel:write")
	public Result<CatalogViews.Channel> createChannel(@Valid @RequestBody CatalogRequests.SaveChannel request) {
		return call(() -> service.saveChannel(null, request));
	}

	@PutMapping("/channels/{id}")
	@SaCheckPermission("ocpx:channel:write")
	public Result<CatalogViews.Channel> updateChannel(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveChannel request) {
		return call(() -> service.saveChannel(id, request));
	}

	@DeleteMapping("/channels/{id}")
	@SaCheckPermission("ocpx:channel:write")
	public Result<Boolean> deleteChannel(@PathVariable String id) {
		return call(() -> {
			service.deleteChannel(id);
			return true;
		});
	}

	@GetMapping("/monitors")
	@SaCheckPermission("ocpx:monitor:read")
	public Result<IPage<CatalogViews.Monitor>> monitors(Page<OcpxMonitorLink> page, String channelId,
			String advertiserId, String keyword) {
		return Result.success(service.monitors(page, channelId, advertiserId, keyword));
	}

	@GetMapping("/monitors/{id}")
	@SaCheckPermission("ocpx:monitor:read")
	public Result<CatalogViews.Monitor> monitor(@PathVariable String id) {
		return call(() -> service.monitor(id));
	}

	@PostMapping("/monitors")
	@SaCheckPermission("ocpx:monitor:write")
	public Result<CatalogViews.Monitor> createMonitor(@Valid @RequestBody CatalogRequests.SaveMonitor request) {
		return call(() -> service.saveMonitor(null, request));
	}

	@PutMapping("/monitors/{id}")
	@SaCheckPermission("ocpx:monitor:write")
	public Result<CatalogViews.Monitor> updateMonitor(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveMonitor request) {
		return call(() -> service.saveMonitor(id, request));
	}

	@DeleteMapping("/monitors/{id}")
	@SaCheckPermission("ocpx:monitor:write")
	public Result<Boolean> deleteMonitor(@PathVariable String id) {
		return call(() -> {
			service.deleteMonitor(id);
			return true;
		});
	}

	@PostMapping("/monitors/{id}/publish")
	@SaCheckPermission("ocpx:monitor:publish")
	public Result<Map<String, Object>> publishMonitor(@PathVariable String id) {
		return call(() -> routePublishService.publish(id));
	}

	@GetMapping("/deduction-rules")
	@SaCheckPermission("ocpx:deduction:read")
	public Result<IPage<CatalogViews.Deduction>> deductions(Page<OcpxDeductionRule> page, String channelId,
			String appLinkId) {
		return Result.success(service.deductions(page, channelId, appLinkId));
	}

	@PostMapping("/deduction-rules")
	@SaCheckPermission("ocpx:deduction:write")
	public Result<CatalogViews.Deduction> createDeduction(@Valid @RequestBody CatalogRequests.SaveDeduction request) {
		return call(() -> service.saveDeduction(null, request));
	}

	@PutMapping("/deduction-rules/{id}")
	@SaCheckPermission("ocpx:deduction:write")
	public Result<CatalogViews.Deduction> updateDeduction(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.SaveDeduction request) {
		return call(() -> service.saveDeduction(id, request));
	}

	@DeleteMapping("/deduction-rules/{id}")
	@SaCheckPermission("ocpx:deduction:write")
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
		catch (BootBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
