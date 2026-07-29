package com.yzytech.scx.subject.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.subject.dto.MediaScopeGrantRequest;
import com.yzytech.scx.subject.dto.MediaScopeUserOption;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.service.MediaScopeManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/subjects/media-scopes")
public class MediaScopeController {

	private final MediaScopeManagementService service;

	@GetMapping
	@SaCheckPermission("subject:media-scope:read")
	public Result<Page<UserSubjectScope>> page(Page<UserSubjectScope> page, String mediaId, String userId) {
		return call(() -> service.page(page, mediaId, userId));
	}

	@GetMapping("/candidates")
	@SaCheckPermission("subject:media-scope:write")
	public Result<Page<MediaScopeUserOption>> candidates(Page<MediaScopeUserOption> page, @RequestParam String mediaId,
			String keyword) {
		return call(() -> service.candidates(page, mediaId, keyword));
	}

	@PostMapping
	@SysLog("授予媒体对象范围")
	@SaCheckPermission("subject:media-scope:write")
	public Result<UserSubjectScope> grant(@Valid @RequestBody MediaScopeGrantRequest request) {
		return call(() -> service.grant(request));
	}

	@DeleteMapping("/{id}")
	@SysLog("撤销媒体对象范围")
	@SaCheckPermission("subject:media-scope:write")
	public Result<Boolean> revoke(@PathVariable String id, @RequestParam String mediaId) {
		return call(() -> service.revoke(id, mediaId));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(404, "资源不存在");
		}
		catch (IllegalStateException e) {
			return Result.fail(409, e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("媒体范围操作失败");
		}
	}

}
