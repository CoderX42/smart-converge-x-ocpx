package com.yzytech.scx.system.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import com.yzytech.scx.system.api.service.AdvertiserAccountProvisioner;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysUserMapper;
import com.yzytech.scx.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvertiserAccountProvisionerImplTest {

	private SysUserMapper userMapper;

	private SysRoleMapper roleMapper;

	private SysUserRoleMapper userRoleMapper;

	private AdvertiserAccountProvisioner provisioner;

	@BeforeEach
	void setUp() {
		userMapper = mock(SysUserMapper.class);
		roleMapper = mock(SysRoleMapper.class);
		userRoleMapper = mock(SysUserRoleMapper.class);
		provisioner = new AdvertiserAccountProvisionerImpl(userMapper, roleMapper, userRoleMapper);
	}

	@Test
	void provisionsTrimmedAdvertiserAccountWithBcryptAndFixedRole() {
		when(userMapper.countByUsernameIncludingDeleted("advertiser-login")).thenReturn(0);
		when(roleMapper.selectOne(any())).thenReturn(advertiserRole());
		doAnswer(invocation -> {
			SysUser user = invocation.getArgument(0);
			user.setId("user-1");
			return 1;
		}).when(userMapper).insert(any(SysUser.class));
		when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);

		AdvertiserAccountProvisioner.ProvisionedAccount result = provisioner.provision(" adv-1 ", " 广告主一 ",
				" advertiser-login ", "Abc1!x");

		ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
		verify(userMapper).insert(userCaptor.capture());
		SysUser stored = userCaptor.getValue();
		assertThat(stored.getUsername()).isEqualTo("advertiser-login");
		assertThat(stored.getNickname()).isEqualTo("广告主一");
		assertThat(stored.getType()).isEqualTo(SystemAccountConstants.USER_TYPE_ADVERTISER);
		assertThat(stored.getShopId()).isEqualTo("adv-1");
		assertThat(stored.getStatus()).isEqualTo("0");
		assertThat(stored.getPassword()).isNotEqualTo("Abc1!x");
		assertThat(BCrypt.checkpw("Abc1!x", stored.getPassword())).isTrue();

		ArgumentCaptor<SysUserRole> relationCaptor = ArgumentCaptor.forClass(SysUserRole.class);
		verify(userRoleMapper).insert(relationCaptor.capture());
		assertThat(relationCaptor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(relationCaptor.getValue().getRoleId()).isEqualTo(SystemAccountConstants.ROLE_ADVERTISER_ID);
		assertThat(result).isEqualTo(new AdvertiserAccountProvisioner.ProvisionedAccount("user-1", "advertiser-login"));
	}

	@Test
	void rejectsUsernameOccupiedByAnyLogicalStateBeforeWriting() {
		when(userMapper.countByUsernameIncludingDeleted("taken-name")).thenReturn(1);

		assertThatThrownBy(() -> provisioner.provision("adv-1", "广告主一", " taken-name ", "Abc1!x"))
			.isInstanceOf(BootBusinessException.class)
			.hasMessage("用户名已存在");
		verify(userMapper, never()).insert(any(SysUser.class));
		verify(userRoleMapper, never()).insert(any(SysUserRole.class));
	}

	@Test
	void rejectsWeakPasswordBeforeWriting() {
		assertThatThrownBy(() -> provisioner.provision("adv-1", "广告主一", "login", "abcdef"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("字母、数字和特殊字符");
		verify(userMapper, never()).countByUsernameIncludingDeleted(any());
		verify(userMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void rejectsMissingOrMisconfiguredFixedRole() {
		when(userMapper.countByUsernameIncludingDeleted("login")).thenReturn(0);
		SysRole invalid = advertiserRole();
		invalid.setType("2");
		when(roleMapper.selectOne(any())).thenReturn(invalid);

		assertThatThrownBy(() -> provisioner.provision("adv-1", "广告主一", "login", "Abc1!x"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("固定广告主角色");
		verify(userMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void rejectsAccountInsertThatDidNotWriteExactlyOneRow() {
		when(userMapper.countByUsernameIncludingDeleted("login")).thenReturn(0);
		when(roleMapper.selectOne(any())).thenReturn(advertiserRole());
		when(userMapper.insert(any(SysUser.class))).thenReturn(0);

		assertThatThrownBy(() -> provisioner.provision("adv-1", "广告主一", "login", "Abc1!x"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("账号创建失败");
		verify(userRoleMapper, never()).insert(any(SysUserRole.class));
	}

	@Test
	void rejectsRoleBindingThatDidNotWriteExactlyOneRow() {
		when(userMapper.countByUsernameIncludingDeleted("login")).thenReturn(0);
		when(roleMapper.selectOne(any())).thenReturn(advertiserRole());
		doAnswer(invocation -> {
			SysUser user = invocation.getArgument(0);
			user.setId("user-1");
			return 1;
		}).when(userMapper).insert(any(SysUser.class));
		when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(0);

		assertThatThrownBy(() -> provisioner.provision("adv-1", "广告主一", "login", "Abc1!x"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("角色绑定失败");
	}

	private SysRole advertiserRole() {
		SysRole role = new SysRole();
		role.setId(SystemAccountConstants.ROLE_ADVERTISER_ID);
		role.setRoleCode(SystemAccountConstants.ROLE_ADVERTISER_CODE);
		role.setType("0");
		return role;
	}

}
