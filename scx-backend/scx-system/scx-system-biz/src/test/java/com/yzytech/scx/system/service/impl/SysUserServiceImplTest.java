package com.yzytech.scx.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.dto.SysUserReqDTO;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.vo.MenuVO;
import com.yzytech.scx.system.handler.SocialLoginFactory;
import com.yzytech.scx.system.mapper.SysMenuMapper;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysRoleMenuMapper;
import com.yzytech.scx.system.mapper.SysSocialAccountMapper;
import com.yzytech.scx.system.mapper.SysUserMapper;
import com.yzytech.scx.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
		menu.setPermission("system:sysuser:page");

		when(roleMapper.listRoleIdsByUserId("user-a")).thenReturn(List.of("1"));
		when(roleMapper.listRoleCodesByUserId("user-a")).thenReturn(List.of("ROLE_ADMIN"));
		when(menuMapper.listMenuByRoleId("1")).thenReturn(List.of(menu));

		SysUser result = service.findUserInfo(user);

		assertThat(result.getRoles()).containsExactly("ROLE_ADMIN");
		assertThat(result.getPermissions()).containsExactly("system:sysuser:page");
		verify(menuMapper).listMenuByRoleId("1");
	}

	@Test
	void genericCreationServicesRejectAdvertiserTypeAndFixedRoleBeforePersistence() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysUserServiceImpl service = service(mock(SysRoleMapper.class), mock(SysUserRoleMapper.class));
		ReflectionTestUtils.setField(service, "baseMapper", userMapper);
		SysUser advertiserType = new SysUser();
		advertiserType.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		advertiserType.setRoles(List.of("role-other"));
		SysUser fixedRole = new SysUser();
		fixedRole.setType("0");
		fixedRole.setRoles(List.of(SystemAccountConstants.ROLE_ADVERTISER_ID));
		SysUserReqDTO remoteRequest = new SysUserReqDTO();
		remoteRequest.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);

		assertThatThrownBy(() -> service.saveUser(advertiserType)).isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.saveUser(fixedRole)).isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.createUser(remoteRequest)).isInstanceOf(BootBusinessException.class);
		verify(userMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void genericUpdateServiceRejectsAssigningFixedRoleToOrdinaryUser() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
		SysUserServiceImpl service = service(mock(SysRoleMapper.class), userRoleMapper);
		ReflectionTestUtils.setField(service, "baseMapper", userMapper);
		SysUser persisted = new SysUser();
		persisted.setId("ordinary-user");
		persisted.setType("0");
		when(userMapper.selectById("ordinary-user")).thenReturn(persisted);
		SysUser request = new SysUser();
		request.setId("ordinary-user");
		request.setType("0");
		request.setRoles(List.of(SystemAccountConstants.ROLE_ADVERTISER_ID));

		assertThatThrownBy(() -> service.updateUser(request)).isInstanceOf(BootBusinessException.class);
		verify(userMapper, never()).updateById(any(SysUser.class));
	}

	@Test
	void genericUpdateServiceRejectsChangingAdvertiserAccountRoles() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysRoleMapper roleMapper = mock(SysRoleMapper.class);
		SysUserServiceImpl service = service(roleMapper, mock(SysUserRoleMapper.class));
		ReflectionTestUtils.setField(service, "baseMapper", userMapper);
		SysUser persisted = new SysUser();
		persisted.setId("advertiser-user");
		persisted.setUsername("advertiser-login");
		persisted.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		persisted.setShopId("adv-1");
		when(userMapper.selectById("advertiser-user")).thenReturn(persisted);
		when(roleMapper.listRoleIdsByUserId("advertiser-user"))
			.thenReturn(List.of(SystemAccountConstants.ROLE_ADVERTISER_ID));
		SysUser request = new SysUser();
		request.setId("advertiser-user");
		request.setUsername("advertiser-login");
		request.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		request.setShopId("adv-1");
		request.setRoles(List.of(SystemAccountConstants.ROLE_ADVERTISER_ID, "role-admin"));

		assertThatThrownBy(() -> service.updateUser(request)).isInstanceOf(BootBusinessException.class);
		verify(userMapper, never()).updateById(any(SysUser.class));
	}

	private SysUserServiceImpl service(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
		return new SysUserServiceImpl(roleMapper, mock(SysMenuMapper.class), userRoleMapper,
				mock(SysRoleMenuMapper.class), mock(SysSocialAccountMapper.class), mock(SocialLoginFactory.class));
	}

}
