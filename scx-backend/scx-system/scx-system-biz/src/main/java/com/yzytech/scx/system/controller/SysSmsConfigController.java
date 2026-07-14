

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysSmsConfig;
import com.yzytech.scx.system.service.ISysSmsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 短信配置
 *
 * @date 2025/7/02
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sms-config")
@Tag(description = "sms-config", name = "短信配置")
public class SysSmsConfigController {

	private final ISysSmsConfigService sysSmsConfigService;

	@Operation(summary = "短信配置列表")
	@SaCheckPermission("system:sms-config:page")
	@GetMapping("/page")
	public Result page(Page page, SysSmsConfig sysSmsConfig) {
		return Result.success(sysSmsConfigService.page(page, Wrappers.query(sysSmsConfig)));
	}

	@Operation(summary = "短信配置查询")
	@SaCheckPermission("system:sms-config:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysSmsConfigService.getById(id));
	}

	@Operation(summary = "短信配置新增")
	@SysLog("新增短信配置")
	@SaCheckPermission("system:sms-config:add")
	@PostMapping
	public Result add(@Valid @RequestBody SysSmsConfig sysSmsConfig) {
		return Result.success(sysSmsConfigService.save(sysSmsConfig));
	}

	@Operation(summary = "短信配置修改")
	@SysLog("修改短信配置")
	@SaCheckPermission("system:sms-config:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody SysSmsConfig sysSmsConfig) {
		return Result.success(sysSmsConfigService.updateSmsConfigById(sysSmsConfig));
	}

	@Operation(summary = "短信配置删除")
	@SysLog("删除短信配置")
	@SaCheckPermission("system:sms-config:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysSmsConfigService.removeById(id));
	}

}
