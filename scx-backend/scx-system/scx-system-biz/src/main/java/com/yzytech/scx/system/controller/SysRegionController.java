

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysRegion;
import com.yzytech.scx.system.service.ISysRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 行政区划
 *
 * @date 2025/5/3
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping({ "/region", "/system/region" })
@Tag(description = "region", name = "行政区划")
public class SysRegionController {

	private final ISysRegionService sysRegionService;

	@Operation(summary = "行政区划列表")
	@SaCheckPermission("system:region:page")
	@GetMapping("/page")
	public Result page(Page page, SysRegion sysRegion) {
		return Result.success(sysRegionService.page(page, Wrappers.query(sysRegion)));
	}

	@Operation(summary = "树形结构行政区划列表")
	@GetMapping("/tree")
	public Result tree() {
		return Result.success(sysRegionService.getTree(null));
	}

	@Operation(summary = "行政区划查询")
	@SaCheckPermission("system:region:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysRegionService.getById(id));
	}

	@Operation(summary = "行政区划新增")
	@SysLog("新增行政区划")
	@SaCheckPermission("system:region:add")
	@PostMapping
	public Result add(@RequestBody SysRegion sysRegion) {
		return Result.success(sysRegionService.save(sysRegion));
	}

	@Operation(summary = "行政区划修改")
	@SysLog("修改行政区划")
	@SaCheckPermission("system:region:edit")
	@PutMapping
	public Result edit(@RequestBody SysRegion sysRegion) {
		return Result.success(sysRegionService.updateById(sysRegion));
	}

	@Operation(summary = "行政区划删除")
	@SysLog("删除行政区划")
	@SaCheckPermission("system:region:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		long count = sysRegionService.count(Wrappers.<SysRegion>lambdaQuery().eq(SysRegion::getParentId, id));
		if (count > 0) {
			return Result.fail("存在下级行政区划，不可删除");
		}
		return Result.success(sysRegionService.removeById(id));
	}

}
