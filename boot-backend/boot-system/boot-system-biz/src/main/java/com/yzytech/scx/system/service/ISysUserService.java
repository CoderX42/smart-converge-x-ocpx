

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.dto.SysUserDTO;
import com.yzytech.scx.system.api.dto.SysUserReqDTO;
import com.yzytech.scx.system.api.entity.SysUser;

/**
 * 系统用户
 *
 * @since 2022/2/26 16:47
 */
public interface ISysUserService extends IService<SysUser> {

	/**
	 * 查询用户信息
	 *
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: com.yzytech.scx.system.common.entity.SysUser
	 */

	SysUser findUserInfo(SysUser sysUser);

	/**
	 * 用户名查询用户信息
	 *
	 * @date 2022/5/31
	 * @param username
	 * @return: com.yzytech.scx.system.common.entity.SysUser
	 */
	SysUser findUserByName(String username);

	/**
	 * 手机号查询用户信息
	 *
	 * @date 2022/5/31
	 * @param phone
	 * @return: com.yzytech.scx.system.common.entity.SysUser
	 */
	SysUser findUserByPhone(String phone);

	/**
	 * 新增用户
	 *
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: boolean
	 */
	boolean saveUser(SysUser sysUser);

	/**
	 * 修改用户
	 *
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: boolean
	 */
	boolean updateUser(SysUser sysUser);

	/**
	 * 用户列表查询
	 *
	 * @date 2022/5/31
	 * @param page
	 * @param sysUser
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.yzytech.scx.system.common.entity.SysUser>
	 */
	IPage<SysUser> adminPage(Page page, SysUser sysUser);

	/**
	 * 删除用户
	 *
	 * @date 2022/7/19
	 * @param sysUser
	 * @return: boolean
	 */
	boolean delUser(SysUser sysUser);

	int getCount(SysUser sysUser);

	boolean createUser(SysUserReqDTO sysUserReqDTO);

	boolean bindAccount(SysUserDTO sysUserDTO);

	SysUser findUserByOpenId(String openId);

	boolean unbindAccount(SysUserDTO sysUserDTO);

}
