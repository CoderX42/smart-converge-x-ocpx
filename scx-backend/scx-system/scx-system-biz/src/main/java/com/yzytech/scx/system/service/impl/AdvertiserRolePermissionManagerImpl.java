package com.yzytech.scx.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.CacheConstants;
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
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 共享广告主角色的唯一受支持权限维护入口。 */
@Service
@RequiredArgsConstructor
public class AdvertiserRolePermissionManagerImpl implements AdvertiserRolePermissionManager {

	private static final Set<String> FULL_SCOPE_REQUIRED_PERMISSIONS = Set.of("ocpx:advertiser:add",
			"ocpx:advertiser:delete", "ocpx:media:view", "ocpx:media:add", "ocpx:media:edit",
			"ocpx:media:delete", "ocpx:deduction-rule:list", "ocpx:deduction-rule:create",
			"ocpx:deduction-rule:update", "ocpx:deduction-rule:delete");

	private static final Set<String> NON_DELEGABLE_PERMISSIONS = Set.of(
			SystemAccountConstants.ADVERTISER_ROLE_MANAGE_PERMISSION,
			SystemAccountConstants.ADVERTISER_RECHARGE_PERMISSION,
			SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION);

	private static final Comparator<SysMenu> MENU_ORDER = Comparator
		.comparing((SysMenu menu) -> menu.getSort() == null ? 0 : menu.getSort())
		.thenComparing(SysMenu::getId);

	private final SysRoleMapper roleMapper;

	private final SysMenuMapper menuMapper;

	private final SysRoleMenuMapper roleMenuMapper;

	private final SysUserMapper userMapper;

	@Override
	public Configuration getConfiguration() {
		assertTrustedCaller();
		SysRole role = requireFixedRole(false);
		return configuration(role, catalog(), advertiserAccounts());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = CacheConstants.MENU_CACHE, allEntries = true)
	public Configuration updateConfiguration(List<String> menuIds, boolean allDataScope) {
		assertTrustedCaller();
		SysRole role = requireFixedRole(true);
		Catalog catalog = catalog();
		Set<String> selected = validateAndClose(menuIds, catalog);
		if (!allDataScope && selected.stream().map(catalog.menuById()::get).anyMatch(this::requiresAllDataScope)) {
			throw new BootBusinessException("媒体、扣量及广告主新增/删除权限必须同时开启全量 OCPX 数据范围");
		}

		Set<String> stored = new LinkedHashSet<>(selected);
		if (allDataScope) {
			stored.add(catalog.allScopeMenuId());
		}
		roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, role.getId()));
		if (!stored.isEmpty()) {
			LocalDateTime now = LocalDateTime.now();
			List<SysRoleMenu> relations = stored.stream().sorted().map(menuId -> relation(role.getId(), menuId, now))
				.toList();
			roleMenuMapper.saveBatch(relations);
		}
		role.setUpdateTime(LocalDateTime.now());
		roleMapper.updateById(role);

		List<SysUser> accounts = advertiserAccounts();
		accounts.forEach(account -> StpUtil.logout(account.getId()));
		return configuration(role, catalog, accounts);
	}

	private Configuration configuration(SysRole role, Catalog catalog, List<SysUser> accounts) {
		Set<String> stored = roleMenuMapper
			.selectList(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, role.getId()))
			.stream()
			.map(SysRoleMenu::getMenuId)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<String> selected = stored.stream()
			.filter(catalog.menuById()::containsKey)
			.sorted()
			.toList();
		return new Configuration(role.getId(), role.getRoleName(), role.getRoleCode(), selected,
				stored.contains(catalog.allScopeMenuId()), accounts.size(), buildTree(catalog));
	}

	private Catalog catalog() {
		List<SysMenu> activeMenus = menuMapper
			.selectList(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getDelFlag, "0").orderByAsc(SysMenu::getSort));
		SysMenu allScopeMenu = activeMenus.stream()
			.filter(menu -> SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION.equals(menu.getPermission()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("广告主全量数据范围权限不存在"));
		Map<String, SysMenu> menuById = new HashMap<>();
		activeMenus.stream().filter(this::isGrantableOcpxMenu).forEach(menu -> menuById.put(menu.getId(), menu));
		return new Catalog(menuById, allScopeMenu.getId());
	}

	private boolean isGrantableOcpxMenu(SysMenu menu) {
		String permission = text(menu.getPermission());
		if (NON_DELEGABLE_PERMISSIONS.contains(permission)
				|| SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION.equals(permission)) {
			return false;
		}
		return StringUtils.hasText(permission) ? permission.startsWith("ocpx:")
				: text(menu.getPath()).startsWith("/ocpx");
	}

	private Set<String> validateAndClose(List<String> menuIds, Catalog catalog) {
		Set<String> selected = new LinkedHashSet<>();
		if (menuIds != null) {
			for (String raw : menuIds) {
				if (!StringUtils.hasText(raw)) {
					continue;
				}
				String id = raw.trim();
				if (!catalog.menuById().containsKey(id)) {
					throw new BootBusinessException("存在无效、停用或不可授予的 OCPX 权限");
				}
				selected.add(id);
			}
		}
		Set<String> closure = new LinkedHashSet<>(selected);
		for (String id : selected) {
			SysMenu menu = catalog.menuById().get(id);
			Set<String> visited = new HashSet<>();
			while (menu != null && StringUtils.hasText(menu.getParentId()) && !"0".equals(menu.getParentId())
					&& visited.add(menu.getParentId())) {
				menu = catalog.menuById().get(menu.getParentId());
				if (menu != null) {
					closure.add(menu.getId());
				}
			}
		}
		return closure;
	}

	private List<MenuNode> buildTree(Catalog catalog) {
		Map<String, List<SysMenu>> children = new HashMap<>();
		catalog.menuById()
			.values()
			.forEach(menu -> children.computeIfAbsent(text(menu.getParentId()), ignored -> new ArrayList<>()).add(menu));
		children.values().forEach(items -> items.sort(MENU_ORDER));
		return catalog.menuById()
			.values()
			.stream()
			.filter(menu -> !catalog.menuById().containsKey(menu.getParentId()))
			.sorted(MENU_ORDER)
			.map(menu -> menuNode(menu, children))
			.toList();
	}

	private MenuNode menuNode(SysMenu menu, Map<String, List<SysMenu>> children) {
		List<MenuNode> childNodes = children.getOrDefault(menu.getId(), List.of())
			.stream()
			.map(child -> menuNode(child, children))
			.toList();
		return new MenuNode(menu.getId(), menu.getParentId(), menu.getName(), menu.getType(), menu.getPermission(),
				requiresAllDataScope(menu), childNodes);
	}

	private boolean requiresAllDataScope(SysMenu menu) {
		return menu != null && FULL_SCOPE_REQUIRED_PERMISSIONS.contains(text(menu.getPermission()));
	}

	private SysRole requireFixedRole(boolean lock) {
		var query = Wrappers.<SysRole>lambdaQuery().eq(SysRole::getId, SystemAccountConstants.ROLE_ADVERTISER_ID);
		if (lock) {
			query.last("FOR UPDATE");
		}
		SysRole role = roleMapper.selectOne(query);
		if (role == null || !SystemAccountConstants.ROLE_ADVERTISER_CODE.equals(role.getRoleCode())
				|| !"0".equals(role.getType()) || role.getShopId() != null) {
			throw new IllegalStateException("固定广告主角色不存在或配置无效");
		}
		return role;
	}

	private void assertTrustedCaller() {
		BootUser user = SecurityUtils.getUser();
		if (user != null && SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "广告主账号不能管理共享角色权限");
		}
	}

	private List<SysUser> advertiserAccounts() {
		return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getType, SystemAccountConstants.USER_TYPE_ADVERTISER)
			.orderByAsc(SysUser::getId));
	}

	private SysRoleMenu relation(String roleId, String menuId, LocalDateTime now) {
		SysRoleMenu relation = new SysRoleMenu();
		relation.setId(IdUtil.getSnowflakeNextIdStr());
		relation.setRoleId(roleId);
		relation.setMenuId(menuId);
		relation.setCreateTime(now);
		return relation;
	}

	private String text(String value) {
		return value == null ? "" : value.trim();
	}

	private record Catalog(Map<String, SysMenu> menuById, String allScopeMenuId) {
	}

}
