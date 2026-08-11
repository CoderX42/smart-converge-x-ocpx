package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotSafeException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.service.OcpxMediaAccountService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
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
@RequestMapping("/ocpx/media-accounts")
public class OcpxMediaAccountController {

	private final OcpxMediaAccountService mediaAccountService;

	@GetMapping
	@SaCheckPermission("ocpx:media-account:view")
	public Result<IPage<MediaAccountViews.ListItem>> list(Page<OcpxMediaAccount> page, String keyword, String channelId,
			String status, Boolean archived) {
		return Result.success(mediaAccountService.list(page, keyword, channelId, status, archived));
	}

	@GetMapping("/channel-options")
	@SaCheckPermission("ocpx:media-account:view")
	public Result<List<MediaAccountViews.ChannelOption>> channelOptions(String keyword) {
		return Result.success(mediaAccountService.channelOptions(keyword));
	}

	@GetMapping("/document-options")
	@SaCheckPermission("ocpx:media-account:view")
	public Result<List<MediaAccountViews.DocumentOption>> documentOptions(@RequestParam String channelId) {
		return call(() -> mediaAccountService.documentOptions(channelId));
	}

	@GetMapping("/owner-options")
	@SaCheckPermission("ocpx:media-account:owner:view")
	public Result<List<MediaAccountViews.OwnerOption>> ownerOptions(@RequestParam String accountId) {
		return call(() -> mediaAccountService.ownerOptions(accountId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:media-account:view")
	public Result<MediaAccountViews.Detail> detail(@PathVariable String id) {
		return call(() -> mediaAccountService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:media-account:add")
	@SysLog("新增媒体账号")
	public Result<MediaAccountViews.Detail> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody MediaAccountRequests.Create request) {
		return call(() -> mediaAccountService.create(idempotencyKey, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:media-account:edit")
	@SysLog("修改媒体账号")
	public Result<MediaAccountViews.Detail> update(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.Update request) {
		return call(() -> mediaAccountService.update(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:media-account:status")
	@SysLog("变更媒体账号状态")
	public Result<MediaAccountViews.Mutation> changeStatus(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.ChangeStatus request) {
		return call(() -> mediaAccountService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:media-account:archive")
	@SysLog("归档媒体账号")
	public Result<MediaAccountViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.VersionedAction request) {
		return call(() -> mediaAccountService.archive(id, request));
	}

	@PostMapping("/{id}/restore")
	@SaCheckPermission("ocpx:media-account:archive")
	@SysLog("恢复媒体账号")
	public Result<MediaAccountViews.Mutation> restore(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.VersionedAction request) {
		return call(() -> mediaAccountService.restore(id, request));
	}

	@GetMapping("/{id}/credentials")
	@SaCheckPermission("ocpx:media-account:credential:view")
	public Result<List<MediaAccountViews.Credential>> credentials(@PathVariable String id) {
		return call(() -> mediaAccountService.credentials(id));
	}

	@PostMapping("/{id}/credentials")
	@SaCheckPermission("ocpx:media-account:credential:add")
	public Result<MediaAccountViews.Credential> createCredential(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.CreateCredential request) {
		// Do not use request-body SysLog here: the payload contains a write-only Secret.
		return call(() -> mediaAccountService.createCredential(id, request));
	}

	@PutMapping("/{id}/credentials/{credentialId}")
	@SaCheckPermission("ocpx:media-account:credential:edit")
	public Result<MediaAccountViews.Credential> updateCredential(@PathVariable String id,
			@PathVariable String credentialId, @Valid @RequestBody MediaAccountRequests.UpdateCredential request) {
		return call(() -> mediaAccountService.updateCredential(id, credentialId, request));
	}

	@PostMapping("/{id}/credentials/{credentialId}:rotate")
	@SaCheckPermission("ocpx:media-account:credential:rotate")
	public Result<MediaAccountViews.Credential> rotateSecret(@PathVariable String id, @PathVariable String credentialId,
			@Valid @RequestBody MediaAccountRequests.RotateSecret request) {
		// Do not use request-body SysLog here: the payload contains a write-only Secret.
		return call(() -> mediaAccountService.rotateSecret(id, credentialId, request));
	}

	@PostMapping("/{id}/credentials/{credentialId}/status")
	@SaCheckPermission("ocpx:media-account:credential:status")
	@SysLog("变更媒体凭据状态")
	public Result<MediaAccountViews.Credential> changeCredentialStatus(@PathVariable String id,
			@PathVariable String credentialId,
			@Valid @RequestBody MediaAccountRequests.ChangeCredentialStatus request) {
		return call(() -> mediaAccountService.changeCredentialStatus(id, credentialId, request));
	}

	@GetMapping("/{id}/owners")
	@SaCheckPermission("ocpx:media-account:owner:view")
	public Result<List<MediaAccountViews.Owner>> owners(@PathVariable String id) {
		return call(() -> mediaAccountService.owners(id));
	}

	@PutMapping("/{id}/owners")
	@SaCheckPermission("ocpx:media-account:owner:edit")
	@SysLog("维护媒体账号负责人")
	public Result<List<MediaAccountViews.Owner>> replaceOwners(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.ReplaceOwners request) {
		return call(() -> mediaAccountService.replaceOwners(id, request));
	}

	@PostMapping("/{id}/connectivity:check")
	@SaCheckPermission("ocpx:media-account:debug")
	@SysLog("媒体账号配置预检")
	public Result<MediaAccountViews.Preflight> preflight(@PathVariable String id,
			@Valid @RequestBody MediaAccountRequests.Preflight request) {
		return call(() -> mediaAccountService.preflight(id, request));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		catch (NotSafeException error) {
			return Result.fail("请先完成二级认证后再执行敏感操作");
		}
		catch (BootBusinessException error) {
			throw error;
		}
		catch (Exception error) {
			return Result.fail("操作失败");
		}
	}

}
