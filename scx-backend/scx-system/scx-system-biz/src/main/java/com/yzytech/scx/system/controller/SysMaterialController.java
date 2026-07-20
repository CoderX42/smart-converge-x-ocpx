

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysMaterial;
import com.yzytech.scx.system.service.ISysMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 素材
 *
 * @since 2022/2/26 16:30
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/material", "/system/material" })
@Tag(description = "material", name = "素材")
public class SysMaterialController {

	private final ISysMaterialService materialService;

	@Operation(summary = "素材列表")
	@SaCheckPermission("system:material:page")
	@GetMapping("/page")
	public Result<IPage<SysMaterial>> page(Page page, SysMaterial material) {
		IPage<SysMaterial> iPage = materialService.getPage(page, material);
		return Result.success(iPage);
	}

	@SysLog("新增素材")
	@Operation(summary = "素材新增")
	@SaCheckPermission("system:material:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SysMaterial material) {
		return Result.success(materialService.save(material));
	}

	@SysLog("修改素材")
	@Operation(summary = "素材修改")
	@SaCheckPermission("system:material:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SysMaterial material) {
		return Result.success(materialService.updateById(material));
	}

	@SysLog("删除素材")
	@Operation(summary = "素材删除")
	@SaCheckPermission("system:material:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(materialService.removeById(id));
	}

}
