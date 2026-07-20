

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysLog;
import com.yzytech.scx.system.service.ISysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志管理
 *
 * @since 2022/2/26 16:39
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/syslog", "/system/syslog" })
@Tag(description = "syslog", name = "日志管理")

public class SysLogController {

	private final ISysLogService sysLogService;

	@Operation(summary = "日志列表")
	@SaCheckPermission("system:syslog:page")
	@GetMapping("/page")
	public Result page(Page page, SysLog sysLog) {
		return Result.success(sysLogService.page(page, Wrappers.query(sysLog)));
	}

	@Operation(summary = "通过id查询日志")
	@SaCheckPermission("system:syslog:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysLogService.getById(id));
	}

}
