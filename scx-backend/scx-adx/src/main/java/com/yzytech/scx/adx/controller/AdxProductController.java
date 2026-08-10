package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ProductRequests;
import com.yzytech.scx.adx.dto.ProductViews;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.service.AdxProductService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/adx/products")
public class AdxProductController {

	private final AdxProductService productService;

	@GetMapping
	@SaCheckPermission("adx:product:view")
	public Result<IPage<ProductViews.ListItem>> list(Page<AdxProduct> page, String keyword, String advertiserId,
			String status, Boolean archived) {
		return Result.success(productService.list(page, keyword, advertiserId, status, archived));
	}

	@GetMapping("/advertiser-options")
	@SaCheckPermission("adx:product:view")
	public Result<List<ProductViews.AdvertiserOption>> advertiserOptions(String keyword) {
		return Result.success(productService.advertiserOptions(keyword));
	}

	@GetMapping("/event-options")
	@SaCheckPermission("adx:product:view")
	public Result<List<ProductViews.EventOption>> eventOptions(String keyword) {
		return Result.success(productService.eventOptions(keyword));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("adx:product:view")
	public Result<ProductViews.Detail> detail(@PathVariable String id) {
		return call(() -> productService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("adx:product:add")
	@SysLog("新增产品")
	public Result<ProductViews.Detail> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody ProductRequests.Create request) {
		return call(() -> productService.create(idempotencyKey, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("adx:product:edit")
	@SysLog("修改产品主数据")
	public Result<ProductViews.Detail> update(@PathVariable String id,
			@Valid @RequestBody ProductRequests.Update request) {
		return call(() -> productService.update(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("adx:product:archive")
	@SysLog("归档产品")
	public Result<ProductViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody ProductRequests.VersionedAction request) {
		return call(() -> productService.archive(id, request));
	}

	@PostMapping("/{id}/restore")
	@SaCheckPermission("adx:product:archive")
	@SysLog("恢复产品")
	public Result<ProductViews.Mutation> restore(@PathVariable String id,
			@Valid @RequestBody ProductRequests.VersionedAction request) {
		return call(() -> productService.restore(id, request));
	}

	@PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("adx:product:edit")
	@SysLog("上传产品Logo")
	public Result<ProductViews.Detail> uploadLogo(@PathVariable String id, @RequestParam long expectedVersion,
			@RequestPart("file") MultipartFile file) {
		return call(() -> productService.uploadLogo(id, expectedVersion, file));
	}

	@DeleteMapping("/{id}/logo")
	@SaCheckPermission("adx:product:edit")
	@SysLog("移除产品Logo")
	public Result<ProductViews.Detail> removeLogo(@PathVariable String id, @RequestParam long expectedVersion) {
		return call(() -> productService.removeLogo(id, expectedVersion));
	}

	@GetMapping("/{id}/logo")
	@SaCheckPermission("adx:product:view")
	public ResponseEntity<InputStreamResource> logo(@PathVariable String id) {
		AdxProductService.LogoDownload download = productService.logo(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.size())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.body(new InputStreamResource(download.inputStream()));
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
