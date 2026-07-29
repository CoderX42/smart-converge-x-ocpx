package com.yzytech.scx.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.creative.entity.*;
import com.yzytech.scx.creative.service.CreativeService;
import com.yzytech.scx.system.api.service.StorageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/creative")
@Tag(name = "素材与创意", description = "素材、落地页、审核与绑定")
public class CreativeController {

	private final CreativeService service;

	private final StorageUploadService storageUploadService;

	@PostMapping("/assets/upload")
	@SaCheckPermission("creative:asset:write")
	@Operation(summary = "上传素材文件")
	public Result<CreativeAsset> upload(@RequestParam String mediaId, @RequestParam String orgId,
			@RequestParam String assetType, @RequestParam String assetName, @RequestParam("file") MultipartFile file) {
		return call(() -> {
			if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024)
				throw new IllegalArgumentException("素材文件为空或超过50MB");
			if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
				throw new IllegalArgumentException("素材文件名不能为空");
			byte[] bytes = file.getBytes();
			String fileHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			String fileUrl = storageUploadService.upload(file.getInputStream(), file.getOriginalFilename(),
					file.getContentType(), file.getSize());
			return service.createAsset(mediaId, orgId, assetType, assetName, fileUrl, file.getContentType(),
					file.getSize(), fileHash);
		});
	}

	@PostMapping("/assets")
	@SaCheckPermission("creative:asset:write")
	@Operation(summary = "创建素材（通过URL引用）")
	public Result<CreativeAsset> create(@RequestParam String mediaId, @RequestParam String orgId,
			@RequestParam String assetType, @RequestParam String assetName, @RequestParam String fileUrl,
			@RequestParam String mimeType, @RequestParam Long fileSize, @RequestParam String fileHash) {
		return call(
				() -> service.createAsset(mediaId, orgId, assetType, assetName, fileUrl, mimeType, fileSize, fileHash));
	}

	@GetMapping("/assets")
	@SaCheckPermission("creative:asset:read")
	@Operation(summary = "素材列表")
	public Result<Page<CreativeAsset>> list(Page<CreativeAsset> page, @RequestParam String mediaId,
			@RequestParam String orgId, @RequestParam(required = false) String status, String keyword) {
		return call(() -> service.listAssets(page, mediaId, orgId, status, keyword));
	}

	@GetMapping("/assets/{id}")
	@SaCheckPermission("creative:asset:read")
	@Operation(summary = "素材详情")
	public Result<CreativeAsset> detail(@PathVariable String id) {
		return call(() -> service.getAsset(id));
	}

	@GetMapping("/assets/{id}/revisions")
	@SaCheckPermission("creative:asset:read")
	@Operation(summary = "素材版本")
	public Result<List<CreativeAssetRevision>> revisions(@PathVariable String id) {
		return call(() -> service.listAssetRevisions(id));
	}

	@PostMapping("/assets/{id}/submit-review")
	@SaCheckPermission("creative:asset:write")
	@Operation(summary = "送审素材")
	public Result<CreativeReviewCase> submitReview(@PathVariable String id) {
		return call(() -> service.submitForReview(id));
	}

	@PostMapping("/review-cases/{id}/review")
	@SaCheckPermission("creative:review")
	@Operation(summary = "审核素材")
	public Result<CreativeReviewCase> review(@PathVariable String id, @RequestParam String decision,
			@RequestParam(required = false) String comment) {
		return call(() -> service.reviewAsset(id, decision, comment));
	}

	@GetMapping("/review-cases")
	@SaCheckPermission("creative:asset:read")
	@Operation(summary = "素材审核任务列表")
	public Result<Page<CreativeReviewCase>> reviewCases(Page<CreativeReviewCase> page, @RequestParam String mediaId,
			@RequestParam String orgId, @RequestParam(required = false) String status) {
		return call(() -> service.listReviewCases(page, mediaId, orgId, status));
	}

	@GetMapping("/review-cases/{id}/logs")
	@SaCheckPermission("creative:asset:read")
	@Operation(summary = "素材审核日志")
	public Result<List<CreativeReviewLog>> reviewLogs(@PathVariable String id) {
		return call(() -> service.listReviewLogs(id));
	}

	@PostMapping("/review-cases/{id}/virus-scan")
	@SaCheckPermission("creative:review")
	@Operation(summary = "记录外部安全扫描结果")
	public Result<CreativeReviewCase> virusScan(@PathVariable String id, @RequestParam String result) {
		return call(() -> service.recordVirusScan(id, result));
	}

	@PostMapping("/assets/{id}/publish")
	@SaCheckPermission("creative:asset:write")
	public Result<CreativeAsset> publish(@PathVariable String id) {
		return call(() -> service.publishAsset(id));
	}

	@PostMapping("/assets/{id}/suspend")
	@SaCheckPermission("creative:asset:write")
	public Result<CreativeAsset> suspend(@PathVariable String id) {
		return call(() -> service.suspendAsset(id));
	}

	@PostMapping("/landing-pages")
	@SaCheckPermission("creative:landing-page:write")
	@Operation(summary = "创建落地页")
	public Result<LandingPage> createLandingPage(@RequestParam String orgId, @RequestParam String pageName,
			@RequestParam String pageType, @RequestParam String targetUrl,
			@RequestParam(required = false) String content) {
		return call(() -> service.createLandingPage(orgId, pageName, pageType, targetUrl, content));
	}

	@GetMapping("/landing-pages")
	@SaCheckPermission("creative:landing-page:read")
	public Result<Page<LandingPage>> listLandingPages(Page<LandingPage> page, @RequestParam String orgId) {
		return call(() -> service.listLandingPages(page, orgId));
	}

	@GetMapping("/landing-pages/{id}")
	@SaCheckPermission("creative:landing-page:read")
	public Result<LandingPage> getLandingPage(@PathVariable String id) {
		return call(() -> service.getLandingPage(id));
	}

	@PutMapping("/landing-pages/{id}")
	@SaCheckPermission("creative:landing-page:write")
	public Result<LandingPage> updateLandingPage(@PathVariable String id, @RequestParam Integer version,
			@RequestParam(required = false) String pageName, @RequestParam(required = false) String pageType,
			@RequestParam(required = false) String targetUrl, @RequestParam(required = false) String content) {
		return call(() -> service.updateLandingPage(id, version, pageName, pageType, targetUrl, content));
	}

	@GetMapping("/landing-pages/{id}/revisions")
	@SaCheckPermission("creative:landing-page:read")
	public Result<List<LandingPageRevision>> landingPageRevisions(@PathVariable String id) {
		return call(() -> service.listLandingPageRevisions(id));
	}

	@PostMapping("/landing-pages/{id}/publish")
	@SaCheckPermission("creative:landing-page:write")
	public Result<LandingPage> publishLandingPage(@PathVariable String id) {
		return call(() -> service.publishLandingPage(id));
	}

	@PostMapping("/bindings")
	@SaCheckPermission("creative:binding:write")
	@Operation(summary = "绑定素材到广告组")
	public Result<CreativeBinding> bind(@RequestParam String adGroupId, @RequestParam String assetId,
			@RequestParam String revisionId, @RequestParam(required = false) String landingPageId) {
		return call(() -> service.bindCreative(adGroupId, assetId, revisionId, landingPageId));
	}

	@GetMapping("/bindings")
	@SaCheckPermission("creative:binding:read")
	@Operation(summary = "广告组创意绑定列表")
	public Result<Page<CreativeBinding>> bindings(Page<CreativeBinding> page, @RequestParam String adGroupId) {
		return call(() -> service.listBindings(page, adGroupId));
	}

	@PostMapping("/bindings/{id}/deactivate")
	@SaCheckPermission("creative:binding:write")
	@Operation(summary = "停用创意绑定")
	public Result<CreativeBinding> deactivateBinding(@PathVariable String id) {
		return call(() -> service.deactivateBinding(id));
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
