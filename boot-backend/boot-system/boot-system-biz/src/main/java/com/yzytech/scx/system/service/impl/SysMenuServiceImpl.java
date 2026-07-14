

package com.yzytech.scx.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.tree.TreeNode;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.entity.SysRoleMenu;
import com.yzytech.scx.system.api.vo.MenuVO;
import com.yzytech.scx.system.mapper.SysMenuMapper;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysRoleMenuMapper;
import com.yzytech.scx.system.service.ISysMenuService;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统菜单
 *
 * @since 2022/2/26 16:47
 */
@Service
@AllArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

	private final SysRoleMenuMapper sysRoleMenuMapper;

	private final SysRoleMapper sysRoleMapper;

	@Override
	@Cacheable(value = CacheConstants.MENU_CACHE, key = "#roleId + '_menu'", unless = "#result.isEmpty()")
	public List<MenuVO> findMenuByRoleId(String roleId) {
		return baseMapper.listMenuByRoleId(roleId);
	}

	@Override
	@CacheEvict(value = CacheConstants.MENU_CACHE, allEntries = true)
	public boolean updateMenuById(SysMenu sysMenu) {
		return this.updateById(sysMenu);
	}

	@Override
	public List<TreeNode<String>> getLoginUserMenuTree() {
		// 查询角色菜单
		Set<MenuVO> all = new HashSet<>();
		List<String> roles = sysRoleMapper.listRoleIdsByUserId(StpUtil.getLoginId().toString());
		for (String roleId : roles) {
			all.addAll(this.findMenuByRoleId(roleId));
		}
		List<TreeNode<String>> treeNodes = all.stream()
			.filter(menuVo -> CommonConstants.MENU_TYPE_0.equals(menuVo.getType()))
			.sorted(Comparator.comparingInt(MenuVO::getSort))
			.map(menu -> {
				TreeNode<String> treeNode = new TreeNode<>();
				treeNode.setId(menu.getId());
				treeNode.setParentId(menu.getParentId());
				treeNode.setName(menu.getName());
				treeNode.setWeight(menu.getSort());
				Map<String, Object> extra = new HashMap<>();
				extra.put("icon", menu.getIcon());
				extra.put("type", menu.getType());
				extra.put("createTime", menu.getCreateTime());
				extra.put("permission", menu.getPermission());
				extra.put("path", menu.getPath());
				extra.put("component", menu.getComponent());
				extra.put("applicationKey", menu.getApplicationKey());
				extra.put("sort", menu.getSort());
				extra.put("redirect", menu.getRedirect());

				JSONObject jsonObject = new JSONObject();
				jsonObject.put("title", menu.getName());
				jsonObject.put("icon", menu.getIcon());
				jsonObject.put("order", menu.getSort());
				extra.put("meta", jsonObject);
				treeNode.setExtra(extra);
				return treeNode;
			})
			.collect(Collectors.toList());
		return treeNodes;
	}

	@Override
	@CacheEvict(value = CacheConstants.MENU_CACHE, allEntries = true)
	public boolean removeMenuById(String id) {
		long count = baseMapper.selectCount(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
		if (count > 0) {
			throw new BootBusinessException("请先删除下级菜单");
		}
		sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getMenuId, id));
		return this.removeById(id);
	}

	@Override
	@CacheEvict(value = CacheConstants.MENU_CACHE, allEntries = true)
	@Transactional(rollbackFor = Exception.class)
	public boolean saveMenu(SysMenu sysMenu) {
		return this.save(sysMenu);
	}

}
