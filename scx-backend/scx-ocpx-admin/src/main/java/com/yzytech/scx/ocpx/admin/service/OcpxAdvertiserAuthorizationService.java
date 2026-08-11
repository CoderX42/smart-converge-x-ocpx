package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserAuthorizationRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserAuthorizationViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserLoginBinding;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserLoginBindingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemMenuMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemRoleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemRoleMenuMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserRoleMapper;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysRoleMenu;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserAuthorizationService {

	public static final String ADVERTISER_ROLE_ID = "role_advertiser";

	private static final String ADVERTISER_ROLE_CODE = "ROLE_ADVERTISER";

	private static final String MANAGED_ROLE_PREFIX = "ROLE_ADV_EXTRA_";

	private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

	private final OcpxAdvertiserLoginBindingMapper bindingMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxSystemUserMapper userMapper;

	private final OcpxSystemUserRoleMapper userRoleMapper;

	private final OcpxSystemRoleMapper roleMapper;

	private final OcpxSystemRoleMenuMapper roleMenuMapper;

	private final OcpxSystemMenuMapper menuMapper;

	private final OcpxAdvertiserChangeAuditMapper auditMapper;

	private final OcpxUserScopeService userScopeService;

	private final OcpxAccessScope accessScope;

	@Transactional
	public OcpxAdvertiserLoginBinding provision(OcpxAdvertiser advertiser, String rawPassword, List<String> roleIds,
			List<String> extraMenuIds) {
		requireMandatoryRole();
		List<String> requestedRoles = normalized(roleIds);
		List<String> requestedMenus = normalized(extraMenuIds);
		if (!requestedRoles.isEmpty() || !requestedMenus.isEmpty()) {
			StpUtil.checkPermission("ocpx:advertiser:grant");
		}
		AuthorizationCatalog catalog = catalog();
		List<SysRole> sharedRoles = validateSharedRoles(requestedRoles, catalog);
		Set<String> extraMenus = validateExtraMenus(requestedMenus, catalog);

		SysUser user = new SysUser();
		user.setUsername(advertiser.getAccount());
		user.setNickname(advertiser.getAdvertiserName());
		user.setPassword(PASSWORD_ENCODER.encode(rawPassword));
		user.setType("2");
		user.setShopId(advertiser.getId());
		user.setStatus("0");
		user.setDelFlag("0");
		userMapper.insert(user);

		String managedRoleId = createManagedRole(advertiser, extraMenus, catalog);
		replaceUserRoles(user.getId(), sharedRoles.stream().map(SysRole::getId).toList(), managedRoleId);

		OcpxAdvertiserLoginBinding binding = new OcpxAdvertiserLoginBinding();
		binding.setAdvertiserId(advertiser.getId());
		binding.setUserId(user.getId());
		binding.setManagedRoleId(managedRoleId);
		binding.setGrantVersion(1L);
		binding.setCreateBy(actor());
		binding.setUpdateBy(actor());
		bindingMapper.insert(binding);
		userScopeService.ensureAdvertiserScope(user.getId(), advertiser.getId());
		return binding;
	}

	@Transactional
	public void syncProfile(OcpxAdvertiser advertiser) {
		OcpxAdvertiserLoginBinding binding = bindingMapper.selectById(advertiser.getId());
		if (binding == null) {
			throw new IllegalStateException("广告主登录绑定缺失，请先修复数据库校验报告");
		}
		SysUser user = userMapper.selectById(binding.getUserId());
		if (user == null || !"2".equals(user.getType())) {
			throw new IllegalStateException("广告主登录账号不存在或类型错误");
		}
		user.setNickname(advertiser.getAdvertiserName());
		user.setPassword(null);
		userMapper.updateById(user);
	}

	public AdvertiserAuthorizationViews.Options options() {
		return options(catalog());
	}

	public AdvertiserAuthorizationViews.Detail detail(String advertiserId) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId);
		OcpxAdvertiserLoginBinding binding = requireBinding(advertiserId, false);
		AuthorizationCatalog catalog = catalog();
		return detail(advertiser, binding, catalog);
	}

	@Transactional
	public AdvertiserAuthorizationViews.Detail update(String advertiserId,
			AdvertiserAuthorizationRequests.Update request) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId);
		OcpxAdvertiserLoginBinding binding = requireBinding(advertiserId, true);
		if (!request.expectedGrantVersion().equals(binding.getGrantVersion())) {
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "账号授权已被其他操作修改，请刷新后重试");
		}
		AuthorizationCatalog catalog = catalog();
		List<SysRole> sharedRoles = validateSharedRoles(normalized(request.roleIds()), catalog);
		Set<String> extraMenus = validateExtraMenus(normalized(request.extraMenuIds()), catalog);

		retireManagedRole(binding.getManagedRoleId());
		String managedRoleId = createManagedRole(advertiser, extraMenus, catalog);
		replaceUserRoles(binding.getUserId(), sharedRoles.stream().map(SysRole::getId).toList(), managedRoleId);
		binding.setManagedRoleId(managedRoleId);
		binding.setGrantVersion(binding.getGrantVersion() + 1);
		binding.setUpdateBy(actor());
		binding.setUpdateTime(LocalDateTime.now());
		bindingMapper.updateById(binding);
		audit(advertiser, "ACCOUNT_GRANT", "广告主账号授权已更新至版本 " + binding.getGrantVersion());
		invalidateSessionAfterCommit(binding.getUserId());
		return detail(advertiser, binding, catalog());
	}

	private AdvertiserAuthorizationViews.Options options(AuthorizationCatalog catalog) {
		SysRole mandatory = requireMandatoryRole();
		List<AdvertiserAuthorizationViews.Role> shared = catalog.safeRoles()
			.stream()
			.map(role -> roleView(role, catalog.roleMenus().getOrDefault(role.getId(), Set.of())))
			.toList();
		AdvertiserAuthorizationViews.Role mandatoryView = roleView(mandatory,
				catalog.roleMenus().getOrDefault(mandatory.getId(), Set.of()));
		return new AdvertiserAuthorizationViews.Options(mandatoryView, shared, buildMenuTree(catalog));
	}

	private AdvertiserAuthorizationViews.Detail detail(OcpxAdvertiser advertiser, OcpxAdvertiserLoginBinding binding,
			AuthorizationCatalog catalog) {
		SysRole mandatory = requireMandatoryRole();
		List<String> assigned = userRoleMapper
			.selectList(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, binding.getUserId()))
			.stream()
			.map(SysUserRole::getRoleId)
			.toList();
		List<String> shared = assigned.stream()
			.filter(id -> !ADVERTISER_ROLE_ID.equals(id) && !id.equals(binding.getManagedRoleId()))
			.sorted()
			.toList();
		Set<String> inherited = new LinkedHashSet<>(catalog.roleMenus().getOrDefault(ADVERTISER_ROLE_ID, Set.of()));
		shared.forEach(id -> inherited.addAll(catalog.roleMenus().getOrDefault(id, Set.of())));
		Set<String> extra = binding.getManagedRoleId() == null ? Set.of()
				: catalog.roleMenus().getOrDefault(binding.getManagedRoleId(), Set.of());
		Set<String> effective = new LinkedHashSet<>(inherited);
		effective.addAll(extra);
		SysUser user = userMapper.selectById(binding.getUserId());
		return new AdvertiserAuthorizationViews.Detail(advertiser.getId(), binding.getUserId(),
				maskAccount(user == null ? advertiser.getAccount() : user.getUsername()),
				roleView(mandatory, catalog.roleMenus().getOrDefault(ADVERTISER_ROLE_ID, Set.of())), shared,
				sorted(inherited), sorted(extra), sorted(effective), binding.getGrantVersion());
	}

	private AuthorizationCatalog catalog() {
		List<SysMenu> menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
			.eq(SysMenu::getDelFlag, "0")
			.orderByAsc(SysMenu::getSort, SysMenu::getId));
		Map<String, SysMenu> menuById = new HashMap<>();
		menus.forEach(menu -> menuById.put(menu.getId(), menu));
		Set<String> selectable = new LinkedHashSet<>();
		for (SysMenu menu : menus) {
			if (isSelectableOcpxMenu(menu)) {
				selectable.add(menu.getId());
				addParents(selectable, menu, menuById);
			}
		}

		List<SysRole> activeRoles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getDelFlag, "0"));
		Map<String, Set<String>> roleMenus = new HashMap<>();
		for (SysRole role : activeRoles) {
			roleMenus.put(role.getId(),
					roleMenuMapper
						.selectList(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, role.getId()))
						.stream()
						.map(SysRoleMenu::getMenuId)
						.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
		}
		List<SysRole> safeRoles = activeRoles.stream()
			.filter(role -> isSafeSharedRole(role, roleMenus, menuById))
			.sorted(Comparator.comparing(SysRole::getRoleName))
			.toList();
		return new AuthorizationCatalog(menuById, selectable, roleMenus, safeRoles);
	}

	private boolean isSafeSharedRole(SysRole role, Map<String, Set<String>> roleMenus, Map<String, SysMenu> menuById) {
		if (!"0".equals(role.getType()) || ADVERTISER_ROLE_CODE.equals(role.getRoleCode())
				|| "ROLE_ADMIN".equals(role.getRoleCode()) || role.getRoleCode().startsWith(MANAGED_ROLE_PREFIX)) {
			return false;
		}
		Set<String> ids = roleMenus.getOrDefault(role.getId(), Set.of());
		if (ids.isEmpty()) {
			return false;
		}
		for (String id : ids) {
			SysMenu menu = menuById.get(id);
			if (menu == null || !isAllowedRoleMenu(menu)) {
				return false;
			}
		}
		return true;
	}

	private boolean isAllowedRoleMenu(SysMenu menu) {
		String permission = text(menu.getPermission());
		if (!permission.isEmpty()) {
			return permission.startsWith("ocpx:") && !isProtectedPermission(permission);
		}
		String path = text(menu.getPath());
		return path.startsWith("/ocpx") || "menu_ocpx_system".equals(menu.getId());
	}

	private boolean isSelectableOcpxMenu(SysMenu menu) {
		String permission = text(menu.getPermission());
		if (!permission.isEmpty()) {
			return permission.startsWith("ocpx:") && !isProtectedPermission(permission);
		}
		return text(menu.getPath()).startsWith("/ocpx");
	}

	private boolean isProtectedPermission(String permission) {
		return permission.startsWith("system:") || permission.startsWith("ocpx:scope:")
				|| "ocpx:advertiser:grant".equals(permission);
	}

	private List<SysRole> validateSharedRoles(List<String> ids, AuthorizationCatalog catalog) {
		Map<String, SysRole> allowed = new HashMap<>();
		catalog.safeRoles().forEach(role -> allowed.put(role.getId(), role));
		List<SysRole> result = new ArrayList<>();
		for (String id : ids) {
			SysRole role = allowed.get(id);
			if (role == null) {
				throw new IllegalArgumentException("共享角色不存在、受保护或包含非 OCPX 权限");
			}
			result.add(role);
		}
		return result;
	}

	private Set<String> validateExtraMenus(List<String> ids, AuthorizationCatalog catalog) {
		Set<String> result = new LinkedHashSet<>();
		for (String id : ids) {
			if (!catalog.selectableMenuIds().contains(id) || !isSelectableOcpxMenu(catalog.menuById().get(id))) {
				throw new IllegalArgumentException("额外菜单不存在、已停用或不是可授予的 OCPX 菜单");
			}
			result.add(id);
		}
		Set<String> closure = new LinkedHashSet<>(result);
		for (String id : result) {
			addParents(closure, catalog.menuById().get(id), catalog.menuById());
		}
		return closure;
	}

	private void addParents(Set<String> target, SysMenu menu, Map<String, SysMenu> menuById) {
		Set<String> visited = new HashSet<>();
		while (menu != null && StringUtils.hasText(menu.getParentId()) && !"0".equals(menu.getParentId())
				&& visited.add(menu.getParentId())) {
			SysMenu parent = menuById.get(menu.getParentId());
			if (parent == null) {
				break;
			}
			target.add(parent.getId());
			menu = parent;
		}
	}

	private String createManagedRole(OcpxAdvertiser advertiser, Set<String> menuIds, AuthorizationCatalog catalog) {
		if (menuIds.isEmpty()) {
			return null;
		}
		for (String id : menuIds) {
			if (!catalog.menuById().containsKey(id)) {
				throw new IllegalArgumentException("额外菜单父级闭包不完整");
			}
		}
		SysRole role = new SysRole();
		role.setRoleName("广告主专属菜单 · " + advertiser.getAdvertiserName());
		role.setRoleCode(
				MANAGED_ROLE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
		role.setRoleDesc("系统托管的广告主账号增补菜单；禁止在通用角色管理中编辑");
		role.setType("2");
		role.setShopId(advertiser.getId());
		role.setDelFlag("0");
		roleMapper.insert(role);
		for (String menuId : menuIds) {
			SysRoleMenu relation = new SysRoleMenu();
			relation.setRoleId(role.getId());
			relation.setMenuId(menuId);
			relation.setCreateTime(LocalDateTime.now());
			roleMenuMapper.insert(relation);
		}
		return role.getId();
	}

	private void retireManagedRole(String roleId) {
		if (!StringUtils.hasText(roleId)) {
			return;
		}
		SysRole role = roleMapper.selectById(roleId);
		if (role == null || !"2".equals(role.getType()) || !role.getRoleCode().startsWith(MANAGED_ROLE_PREFIX)) {
			throw new IllegalStateException("广告主专属角色绑定无效");
		}
		roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
		userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
		roleMapper.deleteById(roleId);
	}

	private void replaceUserRoles(String userId, List<String> sharedRoleIds, String managedRoleId) {
		userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
		Set<String> roleIds = new LinkedHashSet<>();
		roleIds.add(ADVERTISER_ROLE_ID);
		roleIds.addAll(sharedRoleIds);
		if (StringUtils.hasText(managedRoleId)) {
			roleIds.add(managedRoleId);
		}
		for (String roleId : roleIds) {
			SysUserRole relation = new SysUserRole();
			relation.setUserId(userId);
			relation.setRoleId(roleId);
			userRoleMapper.insert(relation);
		}
	}

	private OcpxAdvertiser requireAdvertiser(String advertiserId) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.MANAGE);
		OcpxAdvertiser advertiser = advertiserMapper.selectById(advertiserId);
		if (advertiser == null) {
			throw new IllegalArgumentException("广告主不存在或无权访问");
		}
		return advertiser;
	}

	private OcpxAdvertiserLoginBinding requireBinding(String advertiserId, boolean lock) {
		OcpxAdvertiserLoginBinding binding = bindingMapper.selectOne(Wrappers.<OcpxAdvertiserLoginBinding>lambdaQuery()
			.eq(OcpxAdvertiserLoginBinding::getAdvertiserId, advertiserId)
			.last(lock ? "FOR UPDATE" : ""));
		if (binding == null) {
			throw new IllegalStateException("广告主登录绑定缺失，请先修复数据库校验报告");
		}
		return binding;
	}

	private SysRole requireMandatoryRole() {
		SysRole role = roleMapper.selectById(ADVERTISER_ROLE_ID);
		if (role == null || !ADVERTISER_ROLE_CODE.equals(role.getRoleCode()) || !"0".equals(role.getDelFlag())) {
			throw new IllegalStateException("固定广告主角色不存在或已停用");
		}
		return role;
	}

	private AdvertiserAuthorizationViews.Role roleView(SysRole role, Set<String> menuIds) {
		return new AdvertiserAuthorizationViews.Role(role.getId(), role.getRoleName(), role.getRoleCode(),
				sorted(menuIds));
	}

	private List<AdvertiserAuthorizationViews.Menu> buildMenuTree(AuthorizationCatalog catalog) {
		Set<String> included = catalog.selectableMenuIds();
		return catalog.menuById()
			.values()
			.stream()
			.filter(menu -> included.contains(menu.getId()))
			.filter(menu -> !included.contains(menu.getParentId()))
			.sorted(Comparator.comparing(menu -> menu.getSort() == null ? 0 : menu.getSort()))
			.map(menu -> menuView(menu, catalog))
			.toList();
	}

	private AdvertiserAuthorizationViews.Menu menuView(SysMenu menu, AuthorizationCatalog catalog) {
		List<AdvertiserAuthorizationViews.Menu> children = catalog.menuById()
			.values()
			.stream()
			.filter(child -> catalog.selectableMenuIds().contains(child.getId()))
			.filter(child -> menu.getId().equals(child.getParentId()))
			.sorted(Comparator.comparing(child -> child.getSort() == null ? 0 : child.getSort()))
			.map(child -> menuView(child, catalog))
			.toList();
		return new AdvertiserAuthorizationViews.Menu(menu.getId(), menu.getParentId(), menu.getName(), menu.getType(),
				menu.getPermission(), isSelectableOcpxMenu(menu), children);
	}

	private void audit(OcpxAdvertiser advertiser, String action, String summary) {
		OcpxAdvertiserChangeAudit audit = new OcpxAdvertiserChangeAudit();
		audit.setAdvertiserId(advertiser.getId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(advertiser.getConfigVersion());
		audit.setAfterConfigVersion(advertiser.getConfigVersion());
		audit.setCredentialRevision(
				advertiser.getCredentialRevision() == null ? 0L : advertiser.getCredentialRevision());
		audit.setDocumentRevision(advertiser.getDocumentRevision() == null ? 0L : advertiser.getDocumentRevision());
		audit.setAffectedRouteCount(0);
		audit.setActor(actor());
		audit.setChangeSummary(summary);
		auditMapper.insert(audit);
	}

	private List<String> normalized(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().sorted().toList();
	}

	private List<String> sorted(Set<String> values) {
		return values.stream().sorted().toList();
	}

	private String text(String value) {
		return value == null ? "" : value.trim();
	}

	private String maskAccount(String value) {
		if (!StringUtils.hasText(value) || value.length() < 3) {
			return "***";
		}
		return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "SYSTEM";
	}

	private void invalidateSessionAfterCommit(String userId) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			StpUtil.logout(userId);
			accessScope.invalidate(userId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				StpUtil.logout(userId);
				accessScope.invalidate(userId);
			}
		});
	}

	private record AuthorizationCatalog(Map<String, SysMenu> menuById, Set<String> selectableMenuIds,
			Map<String, Set<String>> roleMenus, List<SysRole> safeRoles) {
	}

}
