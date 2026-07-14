

package com.yzytech.scx.system.api.remote;

import com.yzytech.scx.system.api.dto.SysUserReqDTO;
import com.yzytech.scx.system.api.entity.SysUser;

/**
 * 系统用户 feign
 *
 * @date 2022/6/28
 */
public interface RemoteSysUserService {

	/**
	 * 通过用户名查询用户、角色信息
	 *
	 * @date 2022/6/28
	 * @param username
	 * @return: com.yzytech.scx.system.common.entity.SysUser
	 */
	SysUser getUserInfo(String username);

	/**
	 * 通过手机号查询用户、角色信息
	 *
	 * @date 2022/7/5
	 * @param phone
	 * @return: com.yzytech.scx.common.core.util.Result<com.yzytech.scx.system.common.entity.SysUser>
	 */
	SysUser getUserInfoByPhone(String phone);

	/**
	 * 通过第三方平台临时票据、账号类型查询用户信息
	 * @param code
	 * @param type
	 * @return
	 */
	SysUser getUserInfoBySocialCode(String code, String type);

	/**
	 * 创建系统用户
	 * @param sysUserReqDTO
	 */
	boolean createUser(SysUserReqDTO sysUserReqDTO);

}
