

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.dto.SysRoleMenuDTO;
import com.yzytech.scx.system.api.entity.SysRoleMenu;

/**
 * 角色关联菜单
 *
 * @since 2022/2/26 16:47
 */
public interface ISysRoleMenuService extends IService<SysRoleMenu> {

	boolean saveRoleMenu(SysRoleMenuDTO request);

}
