package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.adx.dto.AccessScopeRequests;
import com.yzytech.scx.adx.dto.AccessScopeViews;
import com.yzytech.scx.adx.service.AdxUserScopeService;
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
@RequestMapping("/adx/access-scopes")
public class AdxAccessScopeController {

	private final AdxUserScopeService service;

	@GetMapping("/users/{userId}")
	@SaCheckPermission("adx:scope:view")
	public Result<AccessScopeViews.UserScopes> get(@PathVariable String userId) {
		return Result.success(service.get(userId));
	}

	@PutMapping("/users/{userId}")
	@SaCheckPermission("adx:scope:edit")
	@SysLog("更新用户 ADX 对象范围")
	public Result<AccessScopeViews.UserScopes> replace(@PathVariable String userId,
			@Valid @RequestBody AccessScopeRequests.Replace request) {
		return Result.success(service.replace(userId, request));
	}

	@GetMapping("/users/{userId}/impact")
	@SaCheckPermission("adx:scope:view")
	public Result<AccessScopeViews.Impact> impact(@PathVariable String userId) {
		return Result.success(service.impact(userId));
	}

	@GetMapping("/options")
	@SaCheckPermission("adx:scope:view")
	public Result<AccessScopeViews.Options> options() {
		return Result.success(service.options());
	}

}
