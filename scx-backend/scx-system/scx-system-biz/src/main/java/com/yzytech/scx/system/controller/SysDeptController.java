

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysDept;
import com.yzytech.scx.system.service.ISysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 部门管理
 *
 * @since 2022/2/26 16:37
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/sysdept")
@Tag(description = "sysdept", name = "部门管理")
public class SysDeptController {

	private final ISysDeptService sysDeptService;

	@Operation(summary = "树形结构部门列表")
	@SaCheckPermission("system:sysdept:page")
	@GetMapping("/tree/list")
	public Result list() {
		return Result.success(sysDeptService.getTreeList());
	}

	@Operation(summary = "部门查询")
	@SaCheckPermission("system:sysdept:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysDeptService.getById(id));
	}

	@Operation(summary = "部门新增")
	@SysLog("新增部门")
	@SaCheckPermission("system:sysdept:add")
	@PostMapping
	public Result add(@RequestBody SysDept sysDept) {
		return Result.success(sysDeptService.save(sysDept));
	}

	@Operation(summary = "部门修改")
	@SysLog("修改部门")
	@SaCheckPermission("system:sysdept:edit")
	@PutMapping
	public Result edit(@RequestBody SysDept sysDept) {
		return Result.success(sysDeptService.updateById(sysDept));
	}

	@Operation(summary = "部门删除")
	@SysLog("删除部门")
	@SaCheckPermission("system:sysdept:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		long count = sysDeptService.count(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id));
		if (count > 0) {
			return Result.fail("存在下级部门，不可删除");
		}
		return Result.success(sysDeptService.removeById(id));
	}

}
