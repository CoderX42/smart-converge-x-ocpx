package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.dto.AccessScopeRequests;
import com.yzytech.scx.ocpx.admin.dto.AccessScopeViews;
import com.yzytech.scx.ocpx.admin.service.OcpxUserScopeService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/access-scopes")
public class OcpxAccessScopeController {

	private final OcpxUserScopeService service;

	@GetMapping("/users/{userId}")
	@SaCheckPermission("ocpx:scope:view")
	public Result<AccessScopeViews.UserScopes> get(@PathVariable String userId) {
		return Result.success(service.get(userId));
	}

	@PutMapping("/users/{userId}")
	@SaCheckPermission("ocpx:scope:edit")
	@SysLog("更新用户 OCPX 对象范围")
	public Result<AccessScopeViews.UserScopes> replace(@PathVariable String userId,
			@Valid @RequestBody AccessScopeRequests.Replace request) {
		return Result.success(service.replace(userId, request));
	}

	@GetMapping("/users/{userId}/impact")
	@SaCheckPermission("ocpx:scope:view")
	public Result<AccessScopeViews.Impact> impact(@PathVariable String userId) {
		return Result.success(service.impact(userId));
	}

	@GetMapping("/options")
	@SaCheckPermission("ocpx:scope:view")
	public Result<AccessScopeViews.Options> options() {
		return Result.success(service.options());
	}

}
