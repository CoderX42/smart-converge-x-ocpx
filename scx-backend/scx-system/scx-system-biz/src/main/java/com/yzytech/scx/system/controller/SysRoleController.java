

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.system.api.dto.SysRoleMenuDTO;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysRoleMenu;
import com.yzytech.scx.system.api.entity.SysUserRole;
import com.yzytech.scx.system.service.ISysRoleMenuService;
import com.yzytech.scx.system.service.ISysRoleService;
import com.yzytech.scx.system.service.ISysUserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 *
 * @since 2022/2/10 16:17
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping({ "/sysrole", "/system/sysrole" })
@Tag(description = "sysrole", name = "角色管理")
public class SysRoleController {

	private final ISysRoleService sysRoleService;

	private final ISysUserRoleService sysUserRoleService;

	private final ISysRoleMenuService sysRoleMenuService;

	@Operation(summary = "角色列表")
	@SaCheckPermission("system:sysrole:page")
	@GetMapping("/page")
	public Result<IPage<SysRole>> page(Page page, SysRole sysRole) {
		return Result.success(sysRoleService.getPage(page, sysRole));
	}

	@Operation(summary = "查询全部角色")
	@SaCheckPermission("system:sysrole:get")
	@GetMapping("/list")
	public Result<List<SysRole>> getList(SysRole sysRole) {
		return Result.success(sysRoleService.list(Wrappers.query(sysRole)
			.lambda()
			.and(role -> role.isNull(SysRole::getType).or().ne(SysRole::getType, "2"))
			.select(SysRole::getRoleName, SysRole::getId)));
	}

	@Operation(summary = "角色查询")
	@SaCheckPermission("system:sysrole:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		SysRole role = sysRoleService.getById(id);
		if (isManaged(role)) {
			return Result.fail("系统托管的广告主专属角色不可在通用角色管理中查看或编辑");
		}
		return Result.success(role);
	}

	@Operation(summary = "角色新增")
	@SysLog("新增角色")
	@SaCheckPermission("system:sysrole:add")
	@PostMapping
	public Result add(@RequestBody SysRole sysRole) {
		if ("2".equals(sysRole.getType())) {
			return Result.fail("系统托管角色只能由广告主账号授权流程创建");
		}
		if (sysRoleService.checkRole(new SysRole(null, sysRole.getRoleCode()))) {
			return Result.fail("角色编码已存在");
		}
		if (sysRoleService.checkRole(new SysRole(sysRole.getRoleName(), null))) {
			return Result.fail("角色名称已存在");
		}
		return Result.success(sysRoleService.save(sysRole));
	}

	@SysLog("修改角色")
	@Operation(summary = "角色修改")
	@SaCheckPermission("system:sysrole:edit")
	@PutMapping
	public Result edit(@RequestBody SysRole sysRole) {
		if (isManaged(sysRoleService.getById(sysRole.getId())) || "2".equals(sysRole.getType())) {
			return Result.fail("系统托管的广告主专属角色不可在通用角色管理中编辑");
		}
		if (sysRoleService.checkRole(new SysRole(sysRole.getId(), sysRole.getRoleName(), null))) {
			return Result.fail("角色名称已存在");
		}
		return Result.success(sysRoleService.updateById(sysRole));
	}

	@Operation(summary = "角色删除")
	@SaCheckPermission("system:sysrole:del")
	@DeleteMapping("/{id}")
	public Result edit(@PathVariable String id) {
		if (isManaged(sysRoleService.getById(id))) {
			return Result.fail("系统托管的广告主专属角色不可在通用角色管理中删除");
		}
		long count = sysUserRoleService.count(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
		if (count > 0) {
			return Result.fail("该角色已被用户绑定，不可删除");
		}
		// 删除角色关联菜单
		sysRoleMenuService.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
		return Result.success(sysRoleService.removeById(id));
	}

	@Operation(summary = "更新角色菜单")
	@SaCheckPermission("system:sysrole:update")
	@PostMapping("/role/menu")
	public Result saveRoleMenu(@RequestBody SysRoleMenuDTO request) {
		if (isManaged(sysRoleService.getById(request.getRoleId()))) {
			return Result.fail("系统托管的广告主专属角色菜单只能由广告主账号授权流程维护");
		}
		return Result.success(sysRoleMenuService.saveRoleMenu(request));
	}

	private boolean isManaged(SysRole role) {
		return role != null && "2".equals(role.getType());
	}

}
