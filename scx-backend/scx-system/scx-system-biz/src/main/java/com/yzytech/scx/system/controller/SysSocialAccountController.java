

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.vo.SysSocialAccountVO;
import com.yzytech.scx.system.service.ISysSocialAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三方平台账号
 *
 * @date 2025/8/9
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/social-account", "/system/social-account" })
@Tag(description = "social-account", name = "三方平台账号")
public class SysSocialAccountController {

	private final ISysSocialAccountService sysSocialAccountService;

	@Operation(summary = "三方平台账号列表")
	@SaCheckPermission("system:social-account:page")
	@GetMapping("/page")
	public Result<IPage<SysSocialAccountVO>> page(Page page, SysSocialAccount sysSocialAccount) {
		return Result.success(sysSocialAccountService.getPage(page, sysSocialAccount));
	}

	@Operation(summary = "三方平台账号查询")
	@SaCheckPermission("system:social-account:get")
	@GetMapping("/{id}")
	public Result<SysSocialAccountVO> getById(@PathVariable String id) {
		return Result.success(sysSocialAccountService.getByAccountId(id));
	}

	@Operation(summary = "三方平台账号新增")
	@SysLog("新增三方平台账号")
	@SaCheckPermission("system:social-account:add")
	@PostMapping
	public Result add(@RequestBody SysSocialAccount sysSocialAccount) {
		return Result.success(sysSocialAccountService.save(sysSocialAccount));
	}

	@Operation(summary = "三方平台账号修改")
	@SysLog("修改三方平台账号")
	@SaCheckPermission("system:social-account:edit")
	@PutMapping
	public Result edit(@RequestBody SysSocialAccount sysSocialAccount) {
		return Result.success(sysSocialAccountService.updateAccountById(sysSocialAccount));
	}

	@Operation(summary = "三方平台账号删除")
	@SysLog("删除三方平台账号")
	@SaCheckPermission("system:social-account:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(sysSocialAccountService.removeById(id));
	}

	@Operation(summary = "三方平台账号查询")
	@GetMapping("/type/{type}")
	public Result getByType(@PathVariable String type) {
		return Result.success(sysSocialAccountService.getOne(Wrappers.<SysSocialAccount>lambdaQuery()
			.eq(SysSocialAccount::getType, type)
			.select(SysSocialAccount::getAppId)));
	}

}
