

package com.yzytech.scx.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.SysUserDTO;
import com.yzytech.scx.system.api.dto.SysUserReqDTO;
import com.yzytech.scx.system.api.entity.*;
import com.yzytech.scx.system.api.vo.MenuVO;
import com.yzytech.scx.system.handler.SocialLoginFactory;
import com.yzytech.scx.system.handler.SocialLoginStrategy;
import com.yzytech.scx.system.mapper.*;
import com.yzytech.scx.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户
 *
 * @since 2022/2/26 16:47
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

	private final SysRoleMapper sysRoleMapper;

	private final SysMenuMapper sysMenuMapper;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final SysRoleMenuMapper sysRoleMenuMapper;

	private final SysSocialAccountMapper sysSocialAccountMapper;

	private final SocialLoginFactory socialLoginFactory;

	@Override
	public SysUser findUserInfo(SysUser sysUser) {

		// 查询角色
		List<String> roleIds = sysRoleMapper.listRoleIdsByUserId(sysUser.getId());
		List<String> roleCodes = sysRoleMapper.listRoleCodesByUserId(sysUser.getId());
		sysUser.setRoles(roleCodes);
		// 权限列表
		Set<String> permissions = new HashSet<>();
		roleIds.forEach(roleId -> {
			List<MenuVO> sysMenus = sysMenuMapper.listMenuByRoleId(roleId);
			List<String> permissionList = sysMenus.stream()
				.filter(menuVo -> StrUtil.isNotEmpty(menuVo.getPermission()))
				.map(MenuVO::getPermission)
				.collect(Collectors.toList());
			permissions.addAll(permissionList);
		});
		sysUser.setPermissions(permissions);
		return sysUser;
	}

	@Override
	public SysUser findUserByName(String username) {
		return baseMapper.selectUserByName(username);
	}

	@Override
	public SysUser findUserByPhone(String phone) {
		return baseMapper.selectUserByPhone(phone);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveUser(SysUser sysUser) {
		if (Objects.nonNull(this.findUserByName(sysUser.getUsername()))) {
			throw new BootBusinessException("用户已存在");
		}
		if (Objects.nonNull(this.findUserByPhone(sysUser.getPhone()))) {
			throw new BootBusinessException("手机号已存在");
		}

		baseMapper.insert(sysUser);
		saveUserRole(sysUser);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateUser(SysUser sysUser) {
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		baseMapper.updateById(sysUser);
		saveUserRole(sysUser);
		return Boolean.TRUE;
	}

	@Override
	public IPage<SysUser> adminPage(Page page, SysUser sysUser) {
		return baseMapper.adminPage(page, sysUser);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean delUser(SysUser sysUser) {
		// 删除用户管理角色
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		// 删除用户
		return super.removeById(sysUser.getId());
	}

	@Override
	public int getCount(SysUser sysUser) {
		return baseMapper.selectCount(sysUser);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean createUser(SysUserReqDTO sysUserReqDTO) {
		SysUser sysUser = this.findUserByName(sysUserReqDTO.getUsername());
		if (Objects.nonNull(sysUser)) {
			log.error("username already exists !");
			return Boolean.FALSE;
		}
		if (Objects.nonNull(this.findUserByPhone(sysUserReqDTO.getPhone()))) {
			log.error("phone already exists !");
			return Boolean.FALSE;
		}
		sysUser = new SysUser();
		sysUser.setPassword(sysUserReqDTO.getPassword());
		sysUser.setPhone(sysUserReqDTO.getPhone());
		sysUser.setUsername(sysUserReqDTO.getUsername());
		sysUser.setStatus(CommonConstants.NORMAL_STATUS);
		sysUser.setNickname(sysUserReqDTO.getUsername());
		sysUser.setType(sysUserReqDTO.getType());
		sysUser.setShopId(sysUserReqDTO.getShopId());

		this.save(sysUser);
		// 查询角色
		List<SysRole> listSysRole = sysRoleMapper.selectList(
				Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, CommonConstants.DEFAULT_ROLE_SHOP_ADMIN_CODE));
		if (CollectionUtils.isEmpty(listSysRole)) {
			return Boolean.FALSE;
		}
		SysRole shopAdminRole = listSysRole.get(0);

		// 通过店铺默认角色查询关联菜单
		List<SysRoleMenu> sysMenus = sysRoleMenuMapper
			.selectList(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, shopAdminRole.getId()));

		// 创建店铺管理员角色
		SysRole sysRole = new SysRole();
		sysRole.setShopId(sysUserReqDTO.getShopId());
		sysRole.setRoleCode(CommonConstants.ROLE_SHOP_ADMIN_CODE);
		sysRole.setType(CommonConstants.YES);
		sysRole.setRoleName(CommonConstants.ROLE_SHOP_ADMIN_NAME);
		sysRoleMapper.insert(sysRole);
		if (!CollectionUtils.isEmpty(sysMenus)) {
			// 创建关联菜单
			List<SysRoleMenu> roleMenus = sysMenus.stream().map(sysRoleMenu -> {
				SysRoleMenu roleMenu = new SysRoleMenu();
				roleMenu.setRoleId(sysRole.getId());
				roleMenu.setMenuId(sysRoleMenu.getMenuId());
				return roleMenu;
			}).collect(Collectors.toList());
			sysRoleMenuMapper.saveBatch(roleMenus);
		}

		SysUserRole sysUserRole = new SysUserRole();
		sysUserRole.setUserId(sysUser.getId());
		sysUserRole.setRoleId(sysRole.getId());
		sysUserRoleMapper.insert(sysUserRole);
		return Boolean.TRUE;
	}

	@Override
	public boolean bindAccount(SysUserDTO sysUserDTO) {
		// 查询三方配置
		SysSocialAccount sysSocialAccount = sysSocialAccountMapper.selectOne(
				Wrappers.<SysSocialAccount>lambdaQuery().eq(SysSocialAccount::getType, sysUserDTO.getAccountType()));
		// 默认微信绑定
		SocialLoginStrategy strategy = socialLoginFactory.getStrategy(sysSocialAccount.getType());
		if (strategy == null) {
			throw new IllegalArgumentException("不支持的社交登录类型: " + sysSocialAccount.getType());
		}
		String openId = strategy.getOpenId(sysUserDTO.getCode(), sysSocialAccount);
		if (!StringUtils.hasText(openId)) {
			throw new BootBusinessException("绑定失败");
		}

		// 查询是否已有绑定记录
		SysUser existingUser = strategy.selectUserByOpenId(openId);
		if (Objects.nonNull(existingUser)) {
			throw new BootBusinessException("该账号已绑定其他账户，请解绑后重新绑定");
		}

		SysUser target = baseMapper.selectById(SecurityUtils.getUserId());
		if (Objects.isNull(target)) {
			throw new BootBusinessException("用户不存在");
		}
		// 调用策略绑定用户信息
		strategy.bindUser(target, openId);
		// 更新用户
		baseMapper.updateById(target);
		return true;
	}

	@Override
	public SysUser findUserByOpenId(String openId) {
		return baseMapper.selectUserByOpenId(openId);
	}

	@Override
	public boolean unbindAccount(SysUserDTO sysUserDTO) {
		SysSocialAccount sysSocialAccount = sysSocialAccountMapper.selectOne(
				Wrappers.<SysSocialAccount>lambdaQuery().eq(SysSocialAccount::getType, sysUserDTO.getAccountType()));

		SocialLoginStrategy strategy = socialLoginFactory.getStrategy(sysSocialAccount.getType());
		if (strategy == null) {
			throw new IllegalArgumentException("不支持的社交登录类型: " + sysSocialAccount.getType());
		}

		SysUser target = baseMapper.selectById(SecurityUtils.getUserId());
		if (Objects.isNull(target)) {
			throw new BootBusinessException("用户不存在");
		}

		return strategy.unbindUser(target);
	}

	private void saveUserRole(SysUser sysUser) {
		SysUserRole sysUserRole = new SysUserRole();
		for (String role : sysUser.getRoles()) {
			sysUserRole.setId(null);
			sysUserRole.setUserId(sysUser.getId());
			sysUserRole.setRoleId(role);
			sysUserRole.insert();
		}
	}

}
