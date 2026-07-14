

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 二级认证
 *
 * @date 2025/7/15
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/secondary-auth")
@Tag(description = "secondary-auth", name = "二级认证")
public class SecondaryAuthController {

	private final ISysUserService sysUserService;

	@SysLog("二级认证")
	@Operation(summary = "二级认证")
	@PostMapping
	public Result add(@RequestBody SysUser sysUser) {
		if (!StringUtils.hasText(sysUser.getPassword())) {
			return Result.fail("密码不能为空");
		}

		SysUser user = sysUserService.getById(SecurityUtils.getUserId());
		if (Objects.isNull(user)) {
			return Result.fail("用户不存在");
		}
		if (!user.getType().equals(CommonConstants.USER_TYPE_PRIMARY)) {
			return Result.fail("无权限");
		}
		if (!BCrypt.checkpw(sysUser.getPassword(), user.getPassword())) {
			return Result.fail("密码错误");
		}
		// 比对成功，为当前会话打开二级认证，有效期为120秒
		StpUtil.openSafe(86400);
		return Result.success();
	}

}
