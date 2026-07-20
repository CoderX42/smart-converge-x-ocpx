

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysAgreement;
import com.yzytech.scx.system.service.ISysAgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 系统协议
 *
 * @date 2025/5/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/agreement", "/system/agreement" })
@Tag(description = "agreement", name = "系统协议")
public class SysAgreementController {

	private final ISysAgreementService sysAgreementService;

	@Operation(summary = "系统协议列表")
	@SaCheckPermission("system:agreement:page")
	@GetMapping("/page")
	public Result page(Page page, SysAgreement sysAgreement) {
		return Result.success(sysAgreementService.page(page, Wrappers.query(sysAgreement)));
	}

	@Operation(summary = "系统协议查询")
	@SaCheckPermission("system:agreement:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysAgreementService.getById(id));
	}

	@Operation(summary = "系统协议新增")
	@SysLog("新增系统协议")
	@SaCheckPermission("system:agreement:add")
	@PostMapping
	public Result add(@RequestBody SysAgreement sysAgreement) {
		return Result.success(sysAgreementService.saveAgreement(sysAgreement));
	}

	@Operation(summary = "系统协议修改")
	@SysLog("修改系统协议")
	@SaCheckPermission("system:agreement:edit")
	@PutMapping
	public Result edit(@RequestBody SysAgreement sysAgreement) {
		return Result.success(sysAgreementService.updateAgreement(sysAgreement));
	}

	@Operation(summary = "系统协议删除")
	@SysLog("删除系统协议")
	@SaCheckPermission("system:agreement:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysAgreementService.removeById(id));
	}

}
