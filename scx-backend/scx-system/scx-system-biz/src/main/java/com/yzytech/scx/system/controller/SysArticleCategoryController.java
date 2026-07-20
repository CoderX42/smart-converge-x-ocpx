

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysArticleCategory;
import com.yzytech.scx.system.service.ISysArticleCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文章分类
 *
 * @date 2025/5/26
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping({ "/article-category", "/system/article-category" })
@Tag(description = "articleCategory", name = "文章分类")
public class SysArticleCategoryController {

	private final ISysArticleCategoryService sysArticleCategoryService;

	@Operation(summary = "文章分类列表")
	@SaCheckPermission("system:article-category:page")
	@GetMapping("/page")
	public Result page(Page page, SysArticleCategory sysArticleCategory) {
		return Result.success(sysArticleCategoryService.page(page, Wrappers.query(sysArticleCategory)));
	}

	@Operation(summary = "查询全部文章分类")
	@SaCheckPermission("system:article-category:get")
	@GetMapping("/list")
	public Result list(SysArticleCategory sysArticleCategory) {
		return Result.success(sysArticleCategoryService.list(Wrappers.query(sysArticleCategory)));
	}

	@Operation(summary = "文章分类查询")
	@SaCheckPermission("system:article-category:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysArticleCategoryService.getById(id));
	}

	@Operation(summary = "文章分类新增")
	@SysLog("新增文章分类")
	@SaCheckPermission("system:article-category:add")
	@PostMapping
	public Result add(@RequestBody SysArticleCategory sysArticleCategory) {
		return Result.success(sysArticleCategoryService.save(sysArticleCategory));
	}

	@Operation(summary = "文章分类修改")
	@SysLog("修改文章分类")
	@SaCheckPermission("system:article-category:edit")
	@PutMapping
	public Result edit(@RequestBody SysArticleCategory sysArticleCategory) {
		return Result.success(sysArticleCategoryService.updateById(sysArticleCategory));
	}

	@Operation(summary = "文章分类删除")
	@SysLog("删除文章分类")
	@SaCheckPermission("system:article-category:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysArticleCategoryService.removeById(id));
	}

}
