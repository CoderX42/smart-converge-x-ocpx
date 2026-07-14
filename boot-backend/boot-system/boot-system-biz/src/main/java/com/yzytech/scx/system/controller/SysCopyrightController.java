

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.entity.SysCopyright;
import com.yzytech.scx.system.api.enums.CopyrightClientTypeEnum;
import com.yzytech.scx.system.service.ISysCopyrightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 系统版权
 *
 * @date 2025/5/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/copyright")
@Tag(description = "copyright", name = "系统版权")
public class SysCopyrightController {

	private final ISysCopyrightService sysCopyrightService;

	@Operation(summary = "系统版权列表")
	@SaCheckPermission("system:copyright:page")
	@GetMapping("/page")
	public Result page(Page page, SysCopyright sysCopyright) {
		return Result.success(sysCopyrightService.page(page, Wrappers.query(sysCopyright)));
	}

	@Operation(summary = "系统版权查询")
	@SaCheckPermission("system:copyright:get")
	@GetMapping("/{clientType}")
	public Result getById(@PathVariable String clientType) {
		if (!StringUtils.hasText(clientType)) {
			return Result.fail("客户端类型不能为空");
		}
		CopyrightClientTypeEnum copyrightClientTypeEnum = CopyrightClientTypeEnum.fromCode(clientType);
		SysCopyright sysCopyright = sysCopyrightService
			.getOne(Wrappers.<SysCopyright>lambdaQuery().eq(SysCopyright::getClientType, clientType));
		if (Objects.isNull(sysCopyright)) {
			sysCopyright = new SysCopyright();
			sysCopyright.setClientType(copyrightClientTypeEnum.getCode());
			sysCopyrightService.save(sysCopyright);
		}
		return Result.success(sysCopyright);
	}

	@Operation(summary = "系统版权新增")
	@SysLog("新增系统版权")
	@SaCheckPermission("system:copyright:add")
	@PostMapping
	public Result add(@RequestBody SysCopyright sysCopyright) {
		return Result.success(sysCopyrightService.save(sysCopyright));
	}

	@Operation(summary = "系统版权修改")
	@SysLog("修改系统版权")
	@SaCheckPermission("system:copyright:edit")
	@PutMapping
	public Result edit(@RequestBody SysCopyright sysCopyright) {
		return Result.success(sysCopyrightService.updateById(sysCopyright));
	}

	@Operation(summary = "系统版权删除")
	@SysLog("删除系统版权")
	@SaCheckPermission("system:copyright:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysCopyrightService.removeById(id));
	}

	@Operation(summary = "系统版权查询")
	@GetMapping("/get/{clientType}")
	public Result getCopyright(@PathVariable String clientType) {
		if (!StringUtils.hasText(clientType)) {
			return Result.fail("客户端类型不能为空");
		}
		CopyrightClientTypeEnum copyrightClientTypeEnum = CopyrightClientTypeEnum.fromCode(clientType);
		SysCopyright sysCopyright = sysCopyrightService.getOne(Wrappers.<SysCopyright>lambdaQuery()
			.eq(SysCopyright::getClientType, copyrightClientTypeEnum.getCode()));
		if (Objects.isNull(sysCopyright)) {
			throw new BootBusinessException("系统版权不存在");
		}
		return Result.success(sysCopyright);
	}

}
