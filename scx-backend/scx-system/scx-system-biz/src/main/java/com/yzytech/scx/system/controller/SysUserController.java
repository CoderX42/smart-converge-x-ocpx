
package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.dto.SysUserDTO;
import com.yzytech.scx.system.api.dto.SysUserMobileDTO;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import com.yzytech.scx.system.api.util.SystemPasswordPolicy;
import com.yzytech.scx.system.service.ISysRoleService;
import com.yzytech.scx.system.service.ISysUserRoleService;
import com.yzytech.scx.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 用户管理
 *
 * @since 2022/2/26 16:45
 */
@RestController
@AllArgsConstructor
@RequestMapping("/system/user")
@Tag(description = "user", name = "用户管理")
public class SysUserController {

	private final ISysUserService sysUserService;

	private final ISysRoleService sysRoleService;

	private final ISysUserRoleService sysUserRoleService;

	private final StringRedisTemplate redisTemplate;

	@Operation(summary = "获取当前用户全部信息")
	@GetMapping("/info")
	public Result info() {
		SysUser sysUser = sysUserService.getById(StpUtil.getLoginId().toString());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户信息为空");
		}
		return Result.success(sysUserService.findUserInfo(sysUser));
	}

	@Operation(summary = "用户列表")
	@SaCheckPermission("system:sysuser:page")
	@GetMapping("/page")
	public Result page(Page page, SysUser sysUser) {
		return Result.success(sysUserService.adminPage(page, sysUser));
	}

	@Operation(summary = "用户列表")
	@GetMapping("/list")
	public Result list(SysUser sysUser) {
		return Result.success(sysUserService.list(Wrappers.query(sysUser)));
	}

	@Operation(summary = "通过id查询用户信息")
	@SaCheckPermission("system:sysuser:get")
	@GetMapping("/{id}")
	public Result<SysUser> getById(@PathVariable String id) {
		SysUser sysUser = sysUserService.getById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在");
		}
		sysUser.setRoles(sysRoleService.findRoleIdsByUserId(id));
		return Result.success(sysUser);
	}

	@SysLog("新增用户")
	@Operation(summary = "用户新增")
	@SaCheckPermission("system:sysuser:add")
	@PostMapping
	public Result add(@RequestBody SysUser sysUser) {
		if (ArrayUtil.isEmpty(sysUser.getRoles())) {
			return Result.fail("角色不能为空");
		}
		if (SystemAccountConstants.USER_TYPE_ADVERTISER.equals(sysUser.getType())
				|| containsAdvertiserRole(sysUser.getRoles())) {
			return Result.fail("广告主账号及固定角色只能由广告主开户流程创建和分配");
		}
		sysUser.setPassword(BCrypt.hashpw(sysUser.getPassword()));
		return Result.success(sysUserService.saveUser(sysUser));
	}

	@SysLog("修改用户")
	@Operation(summary = "用户修改")
	@SaCheckPermission("system:sysuser:edit")
	@PutMapping
	public Result edit(@RequestBody SysUser sysUser) {
		sysUser.setPassword(null);
		sysUser.setPhone(null);
		if (ArrayUtil.isEmpty(sysUser.getRoles())) {
			return Result.fail("角色不能为空");
		}
		SysUser persisted = sysUserService.getById(sysUser.getId());
		if (persisted == null) {
			return Result.fail("用户不存在或已删除");
		}
		if (SystemAccountConstants.USER_TYPE_ADVERTISER.equals(persisted.getType())) {
			List<String> currentRoleIds = sysRoleService.findRoleIdsByUserId(persisted.getId());
			if (!new HashSet<>(currentRoleIds).equals(new HashSet<>(sysUser.getRoles()))) {
				return Result.fail("广告主账号角色请在广告主管理的账号授权中维护");
			}
			sysUser.setUsername(persisted.getUsername());
			sysUser.setType(persisted.getType());
			sysUser.setShopId(persisted.getShopId());
		}
		else if (SystemAccountConstants.USER_TYPE_ADVERTISER.equals(sysUser.getType())
				|| containsAdvertiserRole(sysUser.getRoles())) {
			return Result.fail("通用用户管理不可创建广告主账号或分配固定广告主角色");
		}
		return Result.success(sysUserService.updateUser(sysUser));
	}

	@SysLog("删除用户")
	@Operation(summary = "用户删除")
	@SaCheckPermission("system:sysuser:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		// 查询用户
		SysUser sysUser = sysUserService.getById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在或已删除");
		}
		if (SystemAccountConstants.USER_TYPE_ADVERTISER.equals(sysUser.getType())) {
			return Result.fail("广告主绑定账号不可在通用用户管理中删除");
		}
		// 查询管理员角色
		SysRole sysRole = sysRoleService
			.getOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, CommonConstants.ROLE_ADMIN_CODE));
		long count = sysUserRoleService.count(Wrappers.<SysUserRole>lambdaQuery()
			.eq(SysUserRole::getRoleId, sysRole.getId())
			.eq(SysUserRole::getUserId, sysUser.getId()));
		if (count > 0) {
			return Result.fail("管理员不允许删除");
		}
		return Result.success(sysUserService.delUser(sysUser));
	}

	@Operation(summary = "验证手机号")
	@GetMapping("/check/phone")
	public Result checkPhone(SysUser sysUser) {
		int count = sysUserService.getCount(sysUser);
		return Result.success(count);
	}

	@SysLog("修改密码")
	@Operation(summary = "密码修改")
	@PostMapping("/password")
	public Result editPwd(@RequestBody SysUserDTO sysUserDTO) {
		SysUser sysUser = sysUserService.getById(SecurityUtils.getUserId());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在或已删除");
		}
		if (!StringUtils.hasText(sysUserDTO.getPassword())
				|| !BCrypt.checkpw(sysUserDTO.getPassword(), sysUser.getPassword())) {
			return Result.fail("旧密码错误");
		}
		if (!Objects.equals(sysUserDTO.getNewPassword(), sysUserDTO.getCheckPassword())) {
			return Result.fail("新密码与确认密码不一致");
		}
		try {
			SystemPasswordPolicy.validate(sysUserDTO.getNewPassword());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		sysUser.setPassword(BCrypt.hashpw(sysUserDTO.getNewPassword()));
		return Result.success(sysUserService.updateById(sysUser));
	}

	@SysLog("绑定三方平台账号")
	@Operation(summary = "绑定三方平台账号")
	@PostMapping("/bind-account")
	public Result bind(@RequestBody SysUserDTO sysUserDTO) {
		return Result.success(sysUserService.bindAccount(sysUserDTO));
	}

	@SysLog("解绑三方平台账号")
	@Operation(summary = "解绑三方平台账号")
	@PostMapping("/unbind-account")
	public Result unbind(@RequestBody SysUserDTO sysUserDTO) {
		return Result.success(sysUserService.unbindAccount(sysUserDTO));
	}

	@Operation(summary = "修改个人信息")
	@PostMapping("/update-info")
	public Result update(@RequestBody SysUser sysUser) {
		SysUser newSysUser = sysUserService.getById(SecurityUtils.getUserId());
		newSysUser.setAvatar(sysUser.getAvatar());
		newSysUser.setEmail(sysUser.getEmail());
		newSysUser.setNickname(sysUser.getNickname());
		return Result.success(sysUserService.updateById(newSysUser));
	}

	@Operation(summary = "修改手机号")
	@PostMapping("/update-mobile")
	public Result updateMobile(@RequestBody SysUserMobileDTO sysUserMobileDTO) {
		// 验证短信code
		String codeStr = redisTemplate.opsForValue().get(CacheConstants.SMS_CODE_KEY + sysUserMobileDTO.getCode());
		if (!StringUtils.hasText(codeStr)) {
			return Result.fail("验证码错误");
		}
		if (codeStr.equals(sysUserMobileDTO.getCode())) {
			return Result.fail("验证码错误");
		}
		SysUser sysUser = sysUserService.findUserByPhone(sysUserMobileDTO.getPhone());
		if (Objects.nonNull(sysUser)) {
			return Result.fail("手机号已存在: {}", sysUserMobileDTO.getPhone());
		}
		SysUser newSysUser = sysUserService.getById(SecurityUtils.getUserId());
		newSysUser.setPhone(sysUserMobileDTO.getPhone());
		return Result.success(sysUserService.updateById(newSysUser));
	}

	@SysLog("重置密码")
	@Operation(summary = "重置密码")
	@SaCheckPermission("system:sysuser:password")
	@PostMapping("/reset-password")
	public Result resetPassword(@RequestBody SysUserDTO sysUserDTO) {
		SysUser sysUser = sysUserService.getById(sysUserDTO.getId());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在或已删除");
		}
		if (!Objects.equals(sysUserDTO.getPassword(), sysUserDTO.getCheckPassword())) {
			return Result.fail("密码与确认密码不一致");
		}
		try {
			SystemPasswordPolicy.validate(sysUserDTO.getPassword());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		sysUser.setPassword(BCrypt.hashpw(sysUserDTO.getPassword()));
		return Result.success(sysUserService.updateById(sysUser));
	}

	private boolean containsAdvertiserRole(List<String> roleIds) {
		return roleIds != null && (roleIds.contains(SystemAccountConstants.ROLE_ADVERTISER_ID)
				|| roleIds.contains(SystemAccountConstants.ROLE_ADVERTISER_CODE));
	}

}
