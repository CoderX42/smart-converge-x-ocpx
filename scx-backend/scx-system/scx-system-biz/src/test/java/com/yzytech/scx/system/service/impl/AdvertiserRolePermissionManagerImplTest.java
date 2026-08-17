package com.yzytech.scx.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysRoleMenu;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.service.AdvertiserRolePermissionManager;
import com.yzytech.scx.system.mapper.SysMenuMapper;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysRoleMenuMapper;
import com.yzytech.scx.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvertiserRolePermissionManagerImplTest {

	private SysRoleMapper roleMapper;

	private SysMenuMapper menuMapper;

	private SysRoleMenuMapper roleMenuMapper;

	private SysUserMapper userMapper;

	private AdvertiserRolePermissionManager manager;

	@BeforeEach
	void setUp() {
		roleMapper = mock(SysRoleMapper.class);
		menuMapper = mock(SysMenuMapper.class);
		roleMenuMapper = mock(SysRoleMenuMapper.class);
		userMapper = mock(SysUserMapper.class);
		manager = new AdvertiserRolePermissionManagerImpl(roleMapper, menuMapper, roleMenuMapper, userMapper);
		when(roleMapper.selectOne(any())).thenReturn(fixedRole());
		when(menuMapper.selectList(any())).thenReturn(menus());
		when(userMapper.selectList(any())).thenReturn(List.of());
		when(roleMenuMapper.selectList(any())).thenReturn(List.of());
	}

	@Test
	void readsOnlyGrantableOcpxTreeAndSeparatesAllDataScope() {
		when(roleMenuMapper.selectList(any()))
			.thenReturn(List.of(relation("menu-adv"), relation("perm-adv-edit"), relation("perm-scope")));

		AdvertiserRolePermissionManager.Configuration result = manager.getConfiguration();

		assertThat(result.getRoleCode()).isEqualTo(SystemAccountConstants.ROLE_ADVERTISER_CODE);
		assertThat(result.isAllDataScope()).isTrue();
		assertThat(result.getSelectedMenuIds()).containsExactly("menu-adv", "perm-adv-edit");
		assertThat(flatten(result.getMenuTree())).containsExactlyInAnyOrder("menu-adv", "menu-media", "perm-adv-edit")
			.doesNotContain("perm-manage", "perm-recharge", "perm-deduct", "perm-billing", "perm-scope",
					"menu-system", "menu-system-ocpx");
		assertThat(result.getMenuTree()).filteredOn(node -> "menu-media".equals(node.getId())).singleElement()
			.extracting(AdvertiserRolePermissionManager.MenuNode::isRequiresAllDataScope)
			.isEqualTo(true);
	}

	@Test
	void replacesRoleMenusWithParentClosureAndScopeThenLogsOutAdvertiserAccounts() {
		SysUser account = new SysUser();
		account.setId("advertiser-user-1");
		when(userMapper.selectList(any())).thenReturn(List.of(account));
		when(roleMenuMapper.selectList(any()))
			.thenReturn(List.of(relation("menu-media"), relation("perm-scope")));

		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			AdvertiserRolePermissionManager.Configuration result = manager
				.updateConfiguration(List.of("perm-adv-edit"), true);

			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<SysRoleMenu>> captor = ArgumentCaptor.forClass(List.class);
			verify(roleMenuMapper).delete(any());
			verify(roleMenuMapper).saveBatch(captor.capture());
			assertThat(captor.getValue()).extracting(SysRoleMenu::getMenuId)
				.containsExactlyInAnyOrder("menu-adv", "perm-adv-edit", "perm-scope");
			stp.verify(() -> StpUtil.logout("advertiser-user-1"));
			assertThat(result.getAffectedAccountCount()).isEqualTo(1);
		}
	}

	@Test
	void supportsClearingAllBusinessPermissionsAndScope() {
		AdvertiserRolePermissionManager.Configuration result = manager.updateConfiguration(List.of(), false);

		verify(roleMenuMapper).delete(any());
		verify(roleMenuMapper, never()).saveBatch(any());
		assertThat(result.getSelectedMenuIds()).isEmpty();
		assertThat(result.isAllDataScope()).isFalse();
	}

	@Test
	void failedReplacementDoesNotUpdateRoleOrLogoutAccountsAndDeclaresRollbackAndCacheEviction() throws Exception {
		SysUser account = new SysUser();
		account.setId("advertiser-user-1");
		when(userMapper.selectList(any())).thenReturn(List.of(account));
		doThrow(new IllegalStateException("synthetic write failure")).when(roleMenuMapper).saveBatch(any());

		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			assertThatThrownBy(() -> manager.updateConfiguration(List.of("perm-adv-edit"), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("synthetic write failure");
			stp.verifyNoInteractions();
		}
		verify(roleMapper, never()).updateById(any(SysRole.class));

		var method = AdvertiserRolePermissionManagerImpl.class.getMethod("updateConfiguration", List.class,
				boolean.class);
		assertThat(method.getAnnotation(Transactional.class).rollbackFor()).contains(Exception.class);
		assertThat(method.getAnnotation(CacheEvict.class).allEntries()).isTrue();
	}

	@Test
	void rejectsGlobalOnlyPermissionsWithoutExplicitAllDataScope() {
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("menu-media"), false))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("全量 OCPX 数据范围");
		verify(roleMenuMapper, never()).delete(any());
	}

	@Test
	void rejectsProtectedOrNonOcpxMenuIds() {
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("perm-manage"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("perm-recharge"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("perm-deduct"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("perm-billing"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("menu-system"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		assertThatThrownBy(() -> manager.updateConfiguration(List.of("menu-system-ocpx"), true))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不可授予");
		verify(roleMenuMapper, never()).delete(any());
	}

	@Test
	void advertiserAccountCannotModifyItsOwnSharedRoleEvenIfControllerPermissionIsPolluted() {
		BootUser user = new BootUser();
		user.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThatThrownBy(manager::getConfiguration)
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
			assertThatThrownBy(() -> manager.updateConfiguration(List.of(), false))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
		verify(roleMapper, never()).selectOne(any());
	}

	private List<String> flatten(List<AdvertiserRolePermissionManager.MenuNode> nodes) {
		return nodes.stream()
			.flatMap(node -> java.util.stream.Stream.concat(java.util.stream.Stream.of(node.getId()),
					flatten(node.getChildren()).stream()))
			.toList();
	}

	private SysRole fixedRole() {
		SysRole role = new SysRole();
		role.setId(SystemAccountConstants.ROLE_ADVERTISER_ID);
		role.setRoleCode(SystemAccountConstants.ROLE_ADVERTISER_CODE);
		role.setRoleName("广告主");
		role.setType("0");
		return role;
	}

	private List<SysMenu> menus() {
		return List.of(menu("menu-adv", "0", "广告主管理", "/ocpx/advertiser", "ocpx:advertiser:view", 10),
				menu("perm-adv-edit", "menu-adv", "编辑广告主", "", "ocpx:advertiser:edit", 20),
				menu("perm-recharge", "menu-adv", "充值", "", SystemAccountConstants.ADVERTISER_RECHARGE_PERMISSION,
						30),
				menu("perm-deduct", "menu-adv", "扣减", "", SystemAccountConstants.ADVERTISER_DEDUCT_PERMISSION, 35),
				menu("menu-media", "0", "媒体管理", "/ocpx/media", "ocpx:media:view", 30),
				menu("perm-billing", "menu-media", "计费", "", SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION,
						40),
				menu("perm-manage", "menu-adv", "角色权限", "",
						SystemAccountConstants.ADVERTISER_ROLE_MANAGE_PERMISSION, 80),
				menu("perm-scope", "menu-adv", "全量范围", "",
						SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION, 90),
				menu("menu-system", "0", "系统管理", "/system", "system:menu:view", 100),
				menu("menu-system-ocpx", "0", "伪装系统权限", "/ocpx/system", "system:menu:view", 110));
	}

	private SysMenu menu(String id, String parentId, String name, String path, String permission, int sort) {
		SysMenu menu = new SysMenu();
		menu.setId(id);
		menu.setParentId(parentId);
		menu.setName(name);
		menu.setPath(path);
		menu.setPermission(permission);
		menu.setSort(sort);
		menu.setType(path.isEmpty() ? "1" : "0");
		menu.setDelFlag("0");
		return menu;
	}

	private SysRoleMenu relation(String menuId) {
		SysRoleMenu relation = new SysRoleMenu();
		relation.setRoleId(SystemAccountConstants.ROLE_ADVERTISER_ID);
		relation.setMenuId(menuId);
		return relation;
	}

}
