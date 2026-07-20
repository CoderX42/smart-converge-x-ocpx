

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysSmsSendRecord;
import com.yzytech.scx.system.service.ISysSmsSendRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 短信发送记录
 *
 * @date 2025/7/03
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/sms-send-record", "/system/sms-send-record" })
@Tag(description = "sms-send-record", name = "短信发送记录")
public class SysSmsSendRecordController {

	private final ISysSmsSendRecordService sysSmsSendRecordService;

	@Operation(summary = "短信发送记录列表")
	@SaCheckPermission("system:sms-send-record:page")
	@GetMapping("/page")
	public Result page(Page page, SysSmsSendRecord sysSmsSendRecord) {
		return Result.success(sysSmsSendRecordService.page(page, Wrappers.query(sysSmsSendRecord)));
	}

	@Operation(summary = "短信发送记录查询")
	@SaCheckPermission("system:sms-send-record:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysSmsSendRecordService.getById(id));
	}

	@Operation(summary = "短信发送记录新增")
	@SysLog("新增短信发送记录")
	@SaCheckPermission("system:sms-send-record:add")
	@PostMapping
	public Result add(@Valid @RequestBody SysSmsSendRecord sysSmsSendRecord) {
		return Result.success(sysSmsSendRecordService.save(sysSmsSendRecord));
	}

	@Operation(summary = "短信发送记录修改")
	@SysLog("修改短信发送记录")
	@SaCheckPermission("system:sms-send-record:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody SysSmsSendRecord sysSmsSendRecord) {
		return Result.success(sysSmsSendRecordService.updateById(sysSmsSendRecord));
	}

	@Operation(summary = "短信发送记录删除")
	@SysLog("删除短信发送记录")
	@SaCheckPermission("system:sms-send-record:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysSmsSendRecordService.removeById(id));
	}

}
