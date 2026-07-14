

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysArticle;
import com.yzytech.scx.system.service.ISysArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文章
 *
 * @date 2025/5/26
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/article")
@Tag(description = "article", name = "文章")
public class SysArticleController {

	private final ISysArticleService sysArticleService;

	@Operation(summary = "文章列表")
	@SaCheckPermission("system:article:page")
	@GetMapping("/page")
	public Result page(Page page, SysArticle SysArticle) {
		return Result.success(sysArticleService.page(page, Wrappers.query(SysArticle)));
	}

	@Operation(summary = "文章查询")
	@SaCheckPermission("system:article:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysArticleService.getById(id));
	}

	@Operation(summary = "文章新增")
	@SysLog("新增文章")
	@SaCheckPermission("system:article:add")
	@PostMapping
	public Result add(@RequestBody SysArticle SysArticle) {
		return Result.success(sysArticleService.save(SysArticle));
	}

	@Operation(summary = "文章修改")
	@SysLog("修改文章")
	@SaCheckPermission("system:article:edit")
	@PutMapping
	public Result edit(@RequestBody SysArticle SysArticle) {
		return Result.success(sysArticleService.updateById(SysArticle));
	}

	@Operation(summary = "文章删除")
	@SysLog("删除文章")
	@SaCheckPermission("system:article:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysArticleService.removeById(id));
	}

}
