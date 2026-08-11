package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserAuthorizationRequests;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserLoginBinding;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserLoginBindingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemMenuMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemRoleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemRoleMenuMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserRoleMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysRoleMenu;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxAdvertiserAuthorizationServiceTest {

	private final OcpxAdvertiserLoginBindingMapper bindingMapper = mock(OcpxAdvertiserLoginBindingMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxSystemUserMapper userMapper = mock(OcpxSystemUserMapper.class);

	private final OcpxSystemUserRoleMapper userRoleMapper = mock(OcpxSystemUserRoleMapper.class);

	private final OcpxSystemRoleMapper roleMapper = mock(OcpxSystemRoleMapper.class);

	private final OcpxSystemRoleMenuMapper roleMenuMapper = mock(OcpxSystemRoleMenuMapper.class);

	private final OcpxSystemMenuMapper menuMapper = mock(OcpxSystemMenuMapper.class);

	private final OcpxAdvertiserChangeAuditMapper auditMapper = mock(OcpxAdvertiserChangeAuditMapper.class);

	private final OcpxUserScopeService userScopeService = mock(OcpxUserScopeService.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private OcpxAdvertiserAuthorizationService service;

	@BeforeEach
	void setUp() {
		service = new OcpxAdvertiserAuthorizationService(bindingMapper, advertiserMapper, userMapper, userRoleMapper,
				roleMapper, roleMenuMapper, menuMapper, auditMapper, userScopeService, accessScope);
		SysRole mandatory = role("role_advertiser", "广告主", "ROLE_ADVERTISER", "0");
		when(roleMapper.selectById("role_advertiser")).thenReturn(mandatory);
		when(roleMapper.selectList(any())).thenReturn(List.of(mandatory));
		when(menuMapper.selectList(any())).thenReturn(List.of());
		when(roleMenuMapper.selectList(any())).thenReturn(List.of());
		when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
			((SysUser) invocation.getArgument(0)).setId("user-1");
			return 1;
		});
	}

	@Test
	void minimumProvisionWritesOnlyBcryptPasswordAndMandatoryRole() {
		OcpxAdvertiser advertiser = advertiser();

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			service.provision(advertiser, "password-123", List.of(), List.of());
		}

		ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
		verify(userMapper).insert(user.capture());
		assertThat(user.getValue().getPassword()).startsWith("$2");
		assertThat(new BCryptPasswordEncoder().matches("password-123", user.getValue().getPassword())).isTrue();
		assertThat(user.getValue().getType()).isEqualTo("2");
		assertThat(user.getValue().getStatus()).isEqualTo("0");
		ArgumentCaptor<SysUserRole> relation = ArgumentCaptor.forClass(SysUserRole.class);
		verify(userRoleMapper).insert(relation.capture());
		assertThat(relation.getValue().getRoleId()).isEqualTo("role_advertiser");
		verify(userScopeService).ensureAdvertiserScope("user-1", "advertiser-1");
	}

	@Test
	void profileSyncNeverWritesTheUsersPassword() {
		OcpxAdvertiser advertiser = advertiser();
		OcpxAdvertiserLoginBinding binding = new OcpxAdvertiserLoginBinding();
		binding.setAdvertiserId(advertiser.getId());
		binding.setUserId("user-1");
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setType("2");
		user.setPassword("changed-by-advertiser");
		when(bindingMapper.selectById(advertiser.getId())).thenReturn(binding);
		when(userMapper.selectById("user-1")).thenReturn(user);

		service.syncProfile(advertiser);

		ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
		verify(userMapper).updateById(updated.capture());
		assertThat(updated.getValue().getPassword()).isNull();
		assertThat(updated.getValue().getNickname()).isEqualTo(advertiser.getAdvertiserName());
	}

	@Test
	void rejectsAdministratorRoleEvenWhenClientForgesItsId() {
		SysRole admin = role("role_admin", "管理员", "ROLE_ADMIN", "0");
		when(roleMapper.selectList(any()))
			.thenReturn(List.of(role("role_advertiser", "广告主", "ROLE_ADVERTISER", "0"), admin));

		try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
			assertThatThrownBy(() -> service.provision(advertiser(), "password-123", List.of("role_admin"), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("受保护");
		}

		verify(userMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void rejectsSystemAndUnknownMenusEvenWhenClientForgesTheirIds() {
		SysMenu systemMenu = menu("perm_system_user_page", "menu_ocpx_system", "用户管理", "",
				"system:sysuser:page");
		when(menuMapper.selectList(any())).thenReturn(List.of(systemMenu));

		try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
			assertThatThrownBy(() -> service.provision(advertiser(), "password-123", List.of(),
					List.of("perm_system_user_page"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("不是可授予");
			assertThatThrownBy(() -> service.provision(advertiser(), "password-123", List.of(),
					List.of("forged-menu-id"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("不存在");
		}

		verify(userMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void extraMenuCreatesManagedRoleAndCompletesParentClosure() {
		SysMenu root = menu("menu_ocpx_report", "0", "报表中心", "/ocpx/report", "");
		SysMenu permission = menu("perm_ocpx_report_view", "menu_ocpx_report", "查看报表", "", "ocpx:report:view");
		when(menuMapper.selectList(any())).thenReturn(List.of(root, permission));
		when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
			((SysRole) invocation.getArgument(0)).setId("managed-role-1");
			return 1;
		});

		try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class);
				MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			service.provision(advertiser(), "password-123", List.of(), List.of("perm_ocpx_report_view"));
		}

		ArgumentCaptor<SysRole> managed = ArgumentCaptor.forClass(SysRole.class);
		verify(roleMapper).insert(managed.capture());
		assertThat(managed.getValue().getType()).isEqualTo("2");
		assertThat(managed.getValue().getRoleCode()).startsWith("ROLE_ADV_EXTRA_");
		ArgumentCaptor<SysRoleMenu> menus = ArgumentCaptor.forClass(SysRoleMenu.class);
		verify(roleMenuMapper, org.mockito.Mockito.times(2)).insert(menus.capture());
		assertThat(menus.getAllValues()).extracting(SysRoleMenu::getMenuId)
			.containsExactlyInAnyOrder("menu_ocpx_report", "perm_ocpx_report_view");
	}

	@Test
	void rejectsStaleGrantVersionBeforeChangingRoles() {
		OcpxAdvertiser advertiser = advertiser();
		OcpxAdvertiserLoginBinding binding = new OcpxAdvertiserLoginBinding();
		binding.setAdvertiserId(advertiser.getId());
		binding.setUserId("user-1");
		binding.setGrantVersion(3L);
		when(advertiserMapper.selectById(advertiser.getId())).thenReturn(advertiser);
		when(bindingMapper.selectOne(any())).thenReturn(binding);

		assertThatThrownBy(() -> service.update(advertiser.getId(),
				new AdvertiserAuthorizationRequests.Update(List.of(), List.of(), 2L)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("刷新")
			.satisfies(error -> assertThat(((BootBusinessException) error).getCode()).isEqualTo(409));
		verify(userRoleMapper, never()).delete(any());
	}

	@Test
	void successfulGrantUpdateIncrementsVersionAndInvalidatesTheExistingSession() {
		OcpxAdvertiser advertiser = advertiser();
		OcpxAdvertiserLoginBinding binding = new OcpxAdvertiserLoginBinding();
		binding.setAdvertiserId(advertiser.getId());
		binding.setUserId("user-1");
		binding.setGrantVersion(1L);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setUsername("adv-account");
		SysUserRole mandatory = new SysUserRole();
		mandatory.setUserId("user-1");
		mandatory.setRoleId("role_advertiser");
		when(advertiserMapper.selectById(advertiser.getId())).thenReturn(advertiser);
		when(bindingMapper.selectOne(any())).thenReturn(binding);
		when(userRoleMapper.selectList(any())).thenReturn(List.of(mandatory));
		when(userMapper.selectById("user-1")).thenReturn(user);

		try (MockedStatic<StpUtil> sessions = mockStatic(StpUtil.class);
				MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			var result = service.update(advertiser.getId(),
					new AdvertiserAuthorizationRequests.Update(List.of(), List.of(), 1L));

			assertThat(result.grantVersion()).isEqualTo(2L);
			sessions.verify(() -> StpUtil.logout("user-1"));
		}

		verify(accessScope).invalidate("user-1");
	}

	private OcpxAdvertiser advertiser() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setAccount("adv-account");
		advertiser.setConfigVersion(1L);
		advertiser.setCredentialRevision(0L);
		advertiser.setDocumentRevision(0L);
		return advertiser;
	}

	private SysRole role(String id, String name, String code, String type) {
		SysRole role = new SysRole();
		role.setId(id);
		role.setRoleName(name);
		role.setRoleCode(code);
		role.setType(type);
		role.setDelFlag("0");
		return role;
	}

	private SysMenu menu(String id, String parentId, String name, String path, String permission) {
		SysMenu menu = new SysMenu();
		menu.setId(id);
		menu.setParentId(parentId);
		menu.setName(name);
		menu.setPath(path);
		menu.setPermission(permission);
		menu.setType(permission.isEmpty() ? "0" : "1");
		menu.setDelFlag("0");
		menu.setSort(10);
		return menu;
	}

}
