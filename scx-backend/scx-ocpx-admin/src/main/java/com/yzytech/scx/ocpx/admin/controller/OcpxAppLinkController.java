package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.AppLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.AppLinkViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.service.OcpxAppLinkService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Callable;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/app-links")
public class OcpxAppLinkController {

	private final OcpxAppLinkService appLinkService;

	@GetMapping
	@SaCheckPermission("ocpx:app-link:view")
	public Result<IPage<AppLinkViews.ListItem>> list(Page<OcpxAppLink> page, String keyword, String advertiserId,
			String productId, String linkType, String status, String mappingStatus, Boolean archived) {
		return Result.success(
				appLinkService.list(page, keyword, advertiserId, productId, linkType, status, mappingStatus, archived));
	}

	@GetMapping("/product-options")
	@SaCheckPermission("ocpx:app-link:view")
	public Result<List<AppLinkViews.ProductOption>> productOptions(String keyword, String advertiserId) {
		return Result.success(appLinkService.productOptions(keyword, advertiserId));
	}

	@GetMapping("/document-options")
	@SaCheckPermission("ocpx:app-link:view")
	public Result<List<AppLinkViews.DocumentOption>> documentOptions(@RequestParam String productId) {
		return call(() -> appLinkService.documentOptions(productId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:app-link:view")
	public Result<AppLinkViews.Detail> detail(@PathVariable String id) {
		return call(() -> appLinkService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:app-link:add")
	public Result<AppLinkViews.Detail> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody AppLinkRequests.Create request) {
		// The request contains full URLs. Keep it out of request-body operation logs.
		return call(() -> appLinkService.create(idempotencyKey, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:app-link:edit")
	public Result<AppLinkViews.Detail> update(@PathVariable String id,
			@Valid @RequestBody AppLinkRequests.Update request) {
		// The request contains full URLs. The service writes a metadata-only audit
		// record.
		return call(() -> appLinkService.update(id, request));
	}

	@PostMapping("/{id}/mapping:complete")
	@SaCheckPermission("ocpx:app-link:edit")
	public Result<AppLinkViews.Detail> completeLegacyMapping(@PathVariable String id,
			@Valid @RequestBody AppLinkRequests.CompleteLegacyMapping request) {
		return call(() -> appLinkService.completeLegacyMapping(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:app-link:status")
	public Result<AppLinkViews.Mutation> changeStatus(@PathVariable String id,
			@Valid @RequestBody AppLinkRequests.StatusAction request) {
		return call(() -> appLinkService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:app-link:archive")
	public Result<AppLinkViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody AppLinkRequests.VersionedAction request) {
		return call(() -> appLinkService.archive(id, request));
	}

	@PostMapping("/{id}/restore")
	@SaCheckPermission("ocpx:app-link:archive")
	public Result<AppLinkViews.Mutation> restore(@PathVariable String id,
			@Valid @RequestBody AppLinkRequests.VersionedAction request) {
		return call(() -> appLinkService.restore(id, request));
	}

	@PostMapping("/url:validate")
	@SaCheckPermission("ocpx:app-link:validate")
	public Result<AppLinkViews.ValidationReport> validateUrl(@Valid @RequestBody AppLinkRequests.ValidateUrl request) {
		// This is a syntactic configuration check only. It never resolves or opens the
		// URL.
		return call(() -> appLinkService.validateUrl(request));
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
