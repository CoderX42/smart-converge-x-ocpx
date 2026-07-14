

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysConfig;
import com.yzytech.scx.system.service.ISysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 全局系统配置
 *
 * @date 2025/7/01
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/config")
@Tag(description = "config", name = "全局系统配置")
public class SysConfigController {

	private final ISysConfigService sysConfigService;

	@Operation(summary = "全局系统配置列表")
	@SaCheckPermission("system:config:page")
	@GetMapping("/page")
	public Result page(Page page, SysConfig SysConfig) {
		return Result.success(sysConfigService.page(page, Wrappers.query(SysConfig)));
	}

	@Operation(summary = "全局系统配置查询")
	@SaCheckPermission("system:config:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysConfigService.getById(id));
	}

	@Operation(summary = "全局系统配置新增")
	@SysLog("新增全局系统配置")
	@SaCheckPermission("system:config:add")
	@PostMapping
	public Result add(@RequestBody SysConfig SysConfig) {
		return Result.success(sysConfigService.save(SysConfig));
	}

	@Operation(summary = "全局系统配置修改")
	@SysLog("修改全局系统配置")
	@SaCheckPermission("system:config:edit")
	@PutMapping
	public Result edit(@RequestBody SysConfig SysConfig) {
		return Result.success(sysConfigService.updateById(SysConfig));
	}

	@Operation(summary = "全局系统配置删除")
	@SysLog("删除全局系统配置")
	@SaCheckPermission("system:config:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysConfigService.removeById(id));
	}

}
