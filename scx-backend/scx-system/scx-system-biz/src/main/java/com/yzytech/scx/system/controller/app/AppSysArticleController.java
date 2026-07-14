

package com.yzytech.scx.system.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysArticle;
import com.yzytech.scx.system.service.ISysArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章
 *
 * @date 2025/5/26
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/article")
@Tag(description = "article", name = "文章")
public class AppSysArticleController {

	private final ISysArticleService sysArticleService;

	@Operation(summary = "文章列表")
	@GetMapping("/page")
	public Result page(Page page, SysArticle sysArticle) {
		LambdaQueryWrapper<SysArticle> qw = Wrappers.lambdaQuery();
		qw.eq(StringUtils.hasText(sysArticle.getCategoryId()), SysArticle::getCategoryId, sysArticle.getCategoryId());
		qw.like(StringUtils.hasText(sysArticle.getTitle()), SysArticle::getTitle, sysArticle.getTitle());
		return Result.success(sysArticleService.page(page, qw));
	}

	@Operation(summary = "文章查询")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysArticleService.getById(id));
	}

}
