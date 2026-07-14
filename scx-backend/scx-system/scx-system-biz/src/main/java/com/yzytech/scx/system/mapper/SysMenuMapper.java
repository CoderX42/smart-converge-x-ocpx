

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.system.api.entity.SysMenu;
import com.yzytech.scx.system.api.vo.MenuVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 菜单
 *
 * @since 2022/2/26 16:46
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

	/**
	 * 通过角色主键查询菜单
	 *
	 * @date 2022/7/18
	 * @param roleId
	 * @return: java.util.List<com.yzytech.scx.system.common.vo.MenuVO>
	 */
	List<MenuVO> listMenuByRoleId(String roleId);

}
