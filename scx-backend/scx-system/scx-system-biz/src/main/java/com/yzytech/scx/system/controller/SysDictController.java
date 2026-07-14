

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysDict;
import com.yzytech.scx.system.service.ISysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

/**
 * 字典
 *
 * @date 2022/10/21
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/dict")
@Tag(description = "dict", name = "字典")
public class SysDictController {

	private final ISysDictService sysDictService;

	@Operation(summary = "字典列表")
	@SaCheckPermission("system:sysdict:page")
	@GetMapping("/page")
	public Result page(Page page, SysDict sysDict) {
		return Result.success(sysDictService.page(page, Wrappers.query(sysDict)));
	}

	@Operation(summary = "字典查询")
	@SaCheckPermission("system:sysdict:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysDictService.getById(id));
	}

	@Operation(summary = "字典新增")
	@SysLog("新增字典")
	@SaCheckPermission("system:sysdict:add")
	@PostMapping
	@CacheEvict(value = CacheConstants.DICT_CACHE, allEntries = true)
	public Result add(@RequestBody SysDict sysDict) {
		return Result.success(sysDictService.save(sysDict));
	}

	@Operation(summary = "字典修改")
	@SysLog("修改字典")
	@SaCheckPermission("system:sysdict:edit")
	@PutMapping
	@CacheEvict(value = CacheConstants.DICT_CACHE, key = "#sysDict.type")
	public Result edit(@RequestBody SysDict sysDict) {
		return Result.success(sysDictService.updateById(sysDict));
	}

	@Operation(summary = "字典删除")
	@SysLog("删除字典")
	@SaCheckPermission("system:sysdict:del")
	@DeleteMapping("/{id}")
	@CacheEvict(value = CacheConstants.DICT_CACHE, allEntries = true)
	public Result del(@PathVariable String id) {
		return Result.success(sysDictService.removeById(id));
	}

	@Operation(summary = "字典缓存刷新")
	@GetMapping("/refresh")
	@CacheEvict(value = CacheConstants.DICT_CACHE, allEntries = true)
	public Result refreshCache() {
		return Result.success();
	}

}
