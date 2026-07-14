

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.myabtis.datascope.ShopDataScope;
import com.yzytech.scx.system.api.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统用户
 *
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

	/**
	 * 用户列表查询
	 * @param page
	 * @param sysUser
	 * @date 2022/5/31
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.yzytech.scx.system.common.entity.SysUser>
	 */
	@ShopDataScope
	IPage<SysUser> adminPage(Page page, @Param("query") SysUser sysUser);

	SysUser selectUserByName(@Param("username") String username);

	int selectCount(@Param("query") SysUser sysUser);

	SysUser selectUserByPhone(@Param("phone") String phone);

	SysUser selectUserByOpenId(@Param("openId") String openId);

	SysUser selectUserByMiniOpenId(@Param("miniOpenId") String openId);

}
