

package com.yzytech.scx.system.service;

import cn.hutool.core.lang.tree.TreeNode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.vo.MenuVO;

import java.util.List;

/**
 * 系统菜单
 *
 * @since 2022/2/26 16:47
 */
public interface ISysMenuService extends IService<SysMenu> {

	/**
	 * 通过角色主键查询菜单
	 * @param roleId
	 * @date 2022/7/18
	 * @return: java.util.List<com.yzytech.scx.system.common.vo.MenuVO>
	 */
	List<MenuVO> findMenuByRoleId(String roleId);

	/**
	 * 保存
	 *
	 * @date: 2023/4/27 13:32
	 * @param: [sysMenu]
	 * @return: boolean
	 **/
	boolean saveMenu(SysMenu sysMenu);

	/**
	 * 删除
	 *
	 * @date: 2023/4/27 13:32
	 * @param: [id]
	 * @return: boolean
	 **/
	boolean removeMenuById(String id);

	/**
	 * 修改
	 *
	 * @date: 2023/4/27 13:32
	 * @param: [sysMenu]
	 * @return: boolean
	 **/
	boolean updateMenuById(SysMenu sysMenu);

	/**
	 * 查询登录用户菜单树
	 * @return
	 */
	List<TreeNode<String>> getLoginUserMenuTree();

}
