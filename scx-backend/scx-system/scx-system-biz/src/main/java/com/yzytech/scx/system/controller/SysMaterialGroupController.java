

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysMaterialGroup;
import com.yzytech.scx.system.service.ISysMaterialGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 素材分组
 *
 * @since 2022/2/26 16:29
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/materialgroup", "/system/materialgroup" })
@Tag(description = "materialgroup", name = "素材分组")
public class SysMaterialGroupController {

	private final ISysMaterialGroupService materialGroupService;

	@Operation(summary = "素材分组列表")
	@SaCheckPermission("system:materialgroup:page")
	@GetMapping("/page")
	public Result<IPage<SysMaterialGroup>> page(Page page, SysMaterialGroup sysMaterialGroup) {
		IPage<SysMaterialGroup> iPage = materialGroupService.getPage(page, sysMaterialGroup);
		return Result.success(iPage);
	}

	@Operation(summary = "素材分组列表")
	@SaCheckPermission("system:materialgroup:get")
	@GetMapping("/list")
	public Result<List<SysMaterialGroup>> list(SysMaterialGroup sysMaterialGroup) {
		return Result.success(materialGroupService.getList(sysMaterialGroup));
	}

	@SysLog("新增素材分组")
	@Operation(summary = "素材分组新增")
	@SaCheckPermission("system:materialgroup:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SysMaterialGroup sysMaterialGroup) {
		return Result.success(materialGroupService.save(sysMaterialGroup));
	}

	@SysLog("修改素材分组")
	@Operation(summary = "素材分组修改")
	@SaCheckPermission("system:materialgroup:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SysMaterialGroup sysMaterialGroup) {
		return Result.success(materialGroupService.updateById(sysMaterialGroup));
	}

	@SysLog("删除素材分组")
	@Operation(summary = "素材分组删除")
	@SaCheckPermission("system:materialgroup:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(materialGroupService.removeById(id));
	}

}
