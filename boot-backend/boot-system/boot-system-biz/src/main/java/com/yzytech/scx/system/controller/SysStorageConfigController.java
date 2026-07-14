

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysStorageConfig;
import com.yzytech.scx.system.service.ISysStorageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文件存储配置
 *
 * @since 2022/2/26 16:48
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/storage-config")
@Tag(description = "storageconfig", name = "文件存储配置")
public class SysStorageConfigController {

	private final ISysStorageConfigService sysStorageConfigService;

	@Operation(summary = "文件存储配置列表")
	@SaCheckPermission("system:storageconfig:page")
	@GetMapping("/page")
	public Result page(Page page, SysStorageConfig sysStorageConfig) {
		return Result.success(sysStorageConfigService.page(page, Wrappers.query(sysStorageConfig)));
	}

	@Operation(summary = "文件存储配置查询")
	@SaCheckPermission("system:storageconfig:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysStorageConfigService.getById(id));
	}

	@Operation(summary = "文件存储配置新增")
	@SysLog("新增文件存储配置")
	@SaCheckPermission("system:storageconfig:add")
	@PostMapping
	public Result add(@RequestBody SysStorageConfig sysStorageConfig) {
		return Result.success(sysStorageConfigService.saveStorageConfig(sysStorageConfig));
	}

	@Operation(summary = "文件存储配置修改")
	@SysLog("修改文件存储配置")
	@SaCheckPermission("system:storageconfig:edit")
	@PutMapping
	public Result edit(@RequestBody SysStorageConfig sysStorageConfig) {
		String id = sysStorageConfig.getId();
		if (StrUtil.isBlank(id)) {
			return Result.fail("文件存储配置主键为空");
		}
		return Result.success(sysStorageConfigService.updateStorageConfigById(sysStorageConfig));
	}

	@Operation(summary = "文件存储配置删除")
	@SysLog("删除文件存储配置")
	@SaCheckPermission("system:storageconfig:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysStorageConfigService.removeById(id));
	}

}
