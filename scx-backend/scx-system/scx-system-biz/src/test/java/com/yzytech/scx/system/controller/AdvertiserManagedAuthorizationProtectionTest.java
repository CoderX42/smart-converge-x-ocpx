package com.yzytech.scx.system.controller;

import com.yzytech.scx.system.api.dto.SysRoleMenuDTO;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.service.ISysRoleMenuService;
import com.yzytech.scx.system.service.ISysRoleService;
import com.yzytech.scx.system.service.ISysUserRoleService;
import com.yzytech.scx.system.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvertiserManagedAuthorizationProtectionTest {

	@Test
	void genericRoleEndpointsRejectManagedAdvertiserRoleMutation() {
		ISysRoleService roleService = mock(ISysRoleService.class);
		ISysUserRoleService userRoleService = mock(ISysUserRoleService.class);
		ISysRoleMenuService roleMenuService = mock(ISysRoleMenuService.class);
		SysRole managed = new SysRole();
		managed.setId("managed-role-1");
		managed.setType("2");
		when(roleService.getById(managed.getId())).thenReturn(managed);
		SysRoleController controller = new SysRoleController(roleService, userRoleService, roleMenuService);
		SysRoleMenuDTO menuRequest = new SysRoleMenuDTO();
		menuRequest.setRoleId(managed.getId());

		assertThat(controller.edit(managed).getMsg()).contains("系统托管");
		assertThat(controller.edit(managed.getId()).getMsg()).contains("系统托管");
		assertThat(controller.saveRoleMenu(menuRequest).getMsg()).contains("系统托管");
		verify(roleService, never()).updateById(managed);
		verify(roleMenuService, never()).saveRoleMenu(menuRequest);
	}

	@Test
	void genericRoleEndpointsAlsoProtectAndHideFixedSharedAdvertiserRole() {
		ISysRoleService roleService = mock(ISysRoleService.class);
		ISysUserRoleService userRoleService = mock(ISysUserRoleService.class);
		ISysRoleMenuService roleMenuService = mock(ISysRoleMenuService.class);
		SysRole fixed = new SysRole();
		fixed.setId(SystemAccountConstants.ROLE_ADVERTISER_ID);
		fixed.setRoleCode(SystemAccountConstants.ROLE_ADVERTISER_CODE);
		fixed.setType("0");
		when(roleService.getById(fixed.getId())).thenReturn(fixed);
		SysRoleController controller = new SysRoleController(roleService, userRoleService, roleMenuService);
		SysRoleMenuDTO menuRequest = new SysRoleMenuDTO();
		menuRequest.setRoleId(fixed.getId());

		assertThat(controller.getById(fixed.getId()).getMsg()).contains("广告主");
		assertThat(controller.edit(fixed).getMsg()).contains("系统托管");
		assertThat(controller.edit(fixed.getId()).getMsg()).contains("系统托管");
		assertThat(controller.saveRoleMenu(menuRequest).getMsg()).contains("系统托管");
		verify(roleService, never()).updateById(fixed);
		verify(roleMenuService, never()).saveRoleMenu(menuRequest);
	}

	@Test
	void genericUserEndpointRejectsAdvertiserRoleReplacementAndDeletion() {
		ISysUserService userService = mock(ISysUserService.class);
		ISysRoleService roleService = mock(ISysRoleService.class);
		ISysUserRoleService userRoleService = mock(ISysUserRoleService.class);
		SysUser persisted = new SysUser();
		persisted.setId("advertiser-user-1");
		persisted.setType("2");
		persisted.setUsername("advertiser-login");
		persisted.setShopId("advertiser-1");
		when(userService.getById(persisted.getId())).thenReturn(persisted);
		when(roleService.findRoleIdsByUserId(persisted.getId())).thenReturn(List.of("role_advertiser"));
		SysUserController controller = new SysUserController(userService, roleService, userRoleService,
				mock(StringRedisTemplate.class));
		SysUser request = new SysUser();
		request.setId(persisted.getId());
		request.setRoles(List.of("role_admin"));

		assertThat(controller.edit(request).getMsg()).contains("广告主管理");
		assertThat(controller.del(persisted.getId()).getMsg()).contains("不可在通用用户管理中删除");
		verify(userService, never()).updateUser(request);
		verify(userService, never()).delUser(persisted);
	}

	@Test
	void genericUserCreateRejectsAdvertiserTypeAndFixedRoleAssignment() {
		ISysUserService userService = mock(ISysUserService.class);
		ISysRoleService roleService = mock(ISysRoleService.class);
		SysUserController controller = new SysUserController(userService, roleService, mock(ISysUserRoleService.class),
				mock(StringRedisTemplate.class));
		SysUser advertiserType = new SysUser();
		advertiserType.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		advertiserType.setRoles(List.of("role-other"));
		advertiserType.setPassword("Abc1!x");
		SysUser fixedRole = new SysUser();
		fixedRole.setType("0");
		fixedRole.setRoles(List.of(SystemAccountConstants.ROLE_ADVERTISER_ID));
		fixedRole.setPassword("Abc1!x");

		assertThat(controller.add(advertiserType).getMsg()).contains("广告主开户流程");
		assertThat(controller.add(fixedRole).getMsg()).contains("广告主开户流程");
		verify(userService, never()).saveUser(advertiserType);
		verify(userService, never()).saveUser(fixedRole);
	}

}
