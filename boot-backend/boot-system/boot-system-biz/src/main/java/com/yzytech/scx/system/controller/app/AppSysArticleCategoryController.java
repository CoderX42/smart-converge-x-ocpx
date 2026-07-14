

package com.yzytech.scx.system.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysArticleCategory;
import com.yzytech.scx.system.service.ISysArticleCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章分类
 *
 * @date 2025/5/26
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/article-category")
@Tag(description = "articleCategory", name = "文章分类")
public class AppSysArticleCategoryController {

	private final ISysArticleCategoryService sysArticleCategoryService;

	@Operation(summary = "查询全部文章分类")
	@GetMapping("/list")
	public Result list(SysArticleCategory sysArticleCategory) {
		return Result.success(sysArticleCategoryService.list(Wrappers.query(sysArticleCategory)));
	}

}
