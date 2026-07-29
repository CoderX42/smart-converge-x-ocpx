package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.OrganizationScopeGrantRequest;
import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.subject.entity.UserSubjectScope;
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
@RequestMapping("/ad/commercial/organization-scopes")
public class OrganizationScopeController {

	private final OrganizationScopeService service;

	@GetMapping
	@SaCheckPermission("commercial:organization-scope:read")
	public Result<Page<UserSubjectScope>> page(Page<UserSubjectScope> page, String organizationId, String userId) {
		return call(() -> service.page(page, organizationId, userId));
	}

	@PostMapping
	@SaCheckPermission("commercial:organization-scope:write")
	public Result<UserSubjectScope> grant(@Valid @RequestBody OrganizationScopeGrantRequest request) {
		return call(() -> service.grant(request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("commercial:organization-scope:write")
	public Result<Boolean> revoke(@PathVariable String id, @RequestParam String organizationId) {
		return call(() -> service.revoke(id, organizationId));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(404, "资源不存在");
		}
		catch (Exception e) {
			return Result.fail("组织范围操作失败");
		}
	}

}
