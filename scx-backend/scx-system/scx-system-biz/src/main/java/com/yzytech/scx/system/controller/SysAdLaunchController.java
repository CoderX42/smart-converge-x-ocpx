

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysAdLaunch;
import com.yzytech.scx.system.service.ISysAdLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 启动广告配置
 *
 * @date 2025/6/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/ad-launch", "/system/ad-launch" })
@Tag(description = "ad-launch", name = "启动广告配置")
public class SysAdLaunchController {

	private final ISysAdLaunchService sysAdLaunchService;

	@Operation(summary = "启动广告配置查询")
	@SaCheckPermission("system:ad-launch:get")
	@GetMapping
	public Result getConfig() {
		SysAdLaunch sysAdLaunch = sysAdLaunchService.getConfig();
		if (Objects.isNull(sysAdLaunch)) {
			sysAdLaunch = new SysAdLaunch();
			sysAdLaunch.setCountdownSeconds(3);
			sysAdLaunch.setCountdownPosition("top-right");
			sysAdLaunchService.save(sysAdLaunch);
		}
		return Result.success(sysAdLaunch);
	}

	@Operation(summary = "启动广告配置修改")
	@SysLog("修改启动广告配置")
	@SaCheckPermission("system:ad-launch:edit")
	@PutMapping
	public Result edit(@RequestBody SysAdLaunch sysAdLaunch) {
		return Result.success(sysAdLaunchService.updateById(sysAdLaunch));
	}

}
