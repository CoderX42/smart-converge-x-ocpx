package com.yzytech.scx.system.service.impl;

import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.vo.MenuVO;
import com.yzytech.scx.system.handler.SocialLoginFactory;
import com.yzytech.scx.system.mapper.SysMenuMapper;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysRoleMenuMapper;
import com.yzytech.scx.system.mapper.SysSocialAccountMapper;
import com.yzytech.scx.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceImplTest {

	@Test
	void loginUserExposesRoleCodesWhilePermissionsStillUseRoleIds() {
		SysRoleMapper roleMapper = mock(SysRoleMapper.class);
		SysMenuMapper menuMapper = mock(SysMenuMapper.class);
		SysUserServiceImpl service = new SysUserServiceImpl(roleMapper, menuMapper, mock(SysUserRoleMapper.class),
				mock(SysRoleMenuMapper.class), mock(SysSocialAccountMapper.class), mock(SocialLoginFactory.class));
		SysUser user = new SysUser();
		user.setId("user-a");
		MenuVO menu = new MenuVO();
		menu.setPermission("subject:advertiser:read");

		when(roleMapper.listRoleIdsByUserId("user-a")).thenReturn(List.of("1"));
		when(roleMapper.listRoleCodesByUserId("user-a")).thenReturn(List.of("ROLE_ADMIN"));
		when(menuMapper.listMenuByRoleId("1")).thenReturn(List.of(menu));

		SysUser result = service.findUserInfo(user);

		assertThat(result.getRoles()).containsExactly("ROLE_ADMIN");
		assertThat(result.getPermissions()).containsExactly("subject:advertiser:read");
		verify(menuMapper).listMenuByRoleId("1");
	}
}
