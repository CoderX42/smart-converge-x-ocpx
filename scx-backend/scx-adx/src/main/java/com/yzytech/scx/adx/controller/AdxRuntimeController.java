package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.RuntimeViews;
import com.yzytech.scx.adx.service.AdxRuntimeService;
import com.yzytech.scx.common.core.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/adx/runtime")
public class AdxRuntimeController {

	private final AdxRuntimeService service;

	public AdxRuntimeController(AdxRuntimeService service) {
		this.service = service;
	}

	@GetMapping("/overview")
	@SaCheckPermission("adx:runtime:read")
	public Result<RuntimeViews.Overview> overview() {
		return Result.success(service.overview());
	}

	@GetMapping("/deliveries")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.Delivery>> deliveries(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String status, String direction) {
		return Result.success(service.deliveries(current, size, status, direction));
	}

	@GetMapping("/ingress-dlq")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.IngressDlq>> ingressDlq(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String status) {
		return Result.success(service.ingressDlq(current, size, status));
	}

	@PostMapping("/deliveries/{id}/replay")
	@SaCheckPermission("adx:runtime:replay")
	public Result<Boolean> replay(@PathVariable String id) {
		try {
			service.replayDelivery(id);
			return Result.success(true);
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/ingress-dlq/{id}/replay")
	@SaCheckPermission("adx:runtime:replay")
	public Result<Boolean> replayIngressDlq(@PathVariable String id) {
		try {
			service.replayIngressDlq(id);
			return Result.success(true);
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

}
