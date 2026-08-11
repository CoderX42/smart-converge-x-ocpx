package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotSafeException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.dto.CatalogRequests;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.service.OcpxAccountService;
import com.yzytech.scx.ocpx.admin.service.OcpxAdvertiserService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/ocpx/advertisers")
public class OcpxAdvertiserController {

	private final OcpxAdvertiserService advertiserService;

	private final OcpxAccountService accountService;

	@GetMapping
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<IPage<AdvertiserViews.ListItem>> list(Page<OcpxAdvertiser> page, String keyword, String adapterKey,
			String status, Boolean documentConfigured) {
		return Result.success(advertiserService.list(page, keyword, adapterKey, status, documentConfigured));
	}

	@GetMapping("/adapter-capabilities")
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<List<AdvertiserViews.AdapterCapability>> adapterCapabilities() {
		return Result.success(advertiserService.adapters());
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:view")
	public Result<AdvertiserViews.Detail> detail(@PathVariable String id) {
		return call(() -> advertiserService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:advertiser:add")
	@SysLog("新增广告主")
	public Result<AdvertiserViews.Detail> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody AdvertiserRequests.Create request) {
		return call(() -> advertiserService.create(idempotencyKey, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:advertiser:edit")
	@SysLog("修改广告主接入配置")
	public Result<AdvertiserViews.Detail> update(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.Update request) {
		return call(() -> advertiserService.update(id, request));
	}

	@PostMapping("/{id}/credentials:rotate")
	@SaCheckPermission("ocpx:advertiser:credential")
	@SysLog("轮换广告主凭据")
	public Result<AdvertiserViews.CredentialState> rotateCredential(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.RotateCredential request) {
		return call(() -> advertiserService.rotateCredential(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:advertiser:status")
	@SysLog("变更广告主状态")
	public Result<AdvertiserViews.Mutation> changeStatus(@PathVariable String id,
			@Valid @RequestBody AdvertiserRequests.ChangeStatus request) {
		return call(() -> advertiserService.changeStatus(id, request));
	}

	@GetMapping("/{id}/documents")
	@SaCheckPermission("ocpx:advertiser:document:view")
	public Result<List<AdvertiserViews.Document>> documents(@PathVariable String id) {
		return call(() -> advertiserService.documents(id));
	}

	@PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("ocpx:advertiser:document:edit")
	@SysLog("上传广告主接入资料")
	public Result<AdvertiserViews.Document> uploadDocument(@PathVariable String id,
			@RequestParam String documentType, @RequestParam(required = false) @Size(max = 500) String summary,
			@RequestPart("file") MultipartFile file) {
		return call(() -> advertiserService.uploadDocument(id, documentType, summary, file));
	}

	@PostMapping("/{id}/documents/{documentId}:current")
	@SaCheckPermission("ocpx:advertiser:document:edit")
	@SysLog("切换广告主当前接入资料")
	public Result<AdvertiserViews.Mutation> setCurrentDocument(@PathVariable String id,
			@PathVariable String documentId, @Valid @RequestBody AdvertiserRequests.SetCurrentDocument request) {
		return call(() -> advertiserService.setCurrentDocument(id, documentId, request));
	}

	@PostMapping("/{id}/documents/{documentId}:retire")
	@SaCheckPermission("ocpx:advertiser:document:edit")
	@SysLog("停用广告主接入资料")
	public Result<Boolean> retireDocument(@PathVariable String id, @PathVariable String documentId,
			@Valid @RequestBody AdvertiserRequests.RetireDocument request) {
		return call(() -> {
			advertiserService.retireDocument(id, documentId, request);
			return true;
		});
	}

	@GetMapping("/{id}/documents/{documentId}/content")
	@SaCheckPermission("ocpx:advertiser:document:view")
	@SysLog("下载广告主接入资料")
	public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable String id,
			@PathVariable String documentId) {
		OcpxAdvertiserService.DocumentDownload download = advertiserService.downloadDocument(id, documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.size())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(download.fileName(), StandardCharsets.UTF_8)
				.build()
				.toString())
			.body(new InputStreamResource(download.inputStream()));
	}

	@PostMapping("/{id}/recharge")
	@SaCheckPermission("ocpx:account:recharge")
	@SysLog("广告主账户充值")
	public Result<CatalogViews.AccountMutation> recharge(@PathVariable String id,
			@Valid @RequestBody CatalogRequests.Recharge request) {
		return call(
				() -> accountService.recharge(id, request.requestId(), request.amount(), SecurityUtils.getUserName()));
	}

	@GetMapping("/{id}/ledger")
	@SaCheckPermission("ocpx:account:read")
	public Result<IPage<CatalogViews.AccountEntry>> accountLedger(@PathVariable String id,
			Page<CatalogViews.AccountEntry> page) {
		return call(() -> accountService.ledger(page, id));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (NotSafeException e) {
			return Result.fail("请先完成二级认证后再执行敏感操作");
		}
		catch (BootBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
