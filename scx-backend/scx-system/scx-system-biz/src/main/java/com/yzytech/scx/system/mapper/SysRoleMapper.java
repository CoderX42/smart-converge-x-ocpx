
package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.myabtis.datascope.ShopDataScope;
import com.yzytech.scx.system.api.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色
 *
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

	/**
	 * 通过用户id查询角色
	 *
	 * @date 2022/8/23
	 * @param userId
	 * @return: java.util.List<java.lang.String>
	 */
	List<String> listRoleIdsByUserId(String userId);

	/**
	 * 通过用户 id 查询角色编码，用于构造登录用户的角色集合。
	 * @param userId 用户 id
	 * @return 角色编码列表
	 */
	List<String> listRoleCodesByUserId(String userId);

	/**
	 * 分页查询
	 * @param page
	 * @param sysRole
	 * @return
	 */
	@ShopDataScope
	IPage<SysRole> selectRolePage(Page page, @Param("query") SysRole sysRole);

	SysRole selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") String id);

}
