

package com.yzytech.scx.system.handler;

import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.entity.SysUser;

/**
 * 社交登录策略接口 定义不同第三方登录的统一方法
 *
 */
public interface SocialLoginStrategy {

	/**
	 * 根据授权 code 和三方配置获取 openId
	 * @param code 前端传递的授权码
	 * @param account 三方应用配置（AppId、Secret 等）
	 * @return 第三方用户唯一标识 openId
	 */
	String getOpenId(String code, SysSocialAccount account);

	/**
	 * 返回该策略支持的登录类型
	 * @return 登录类型标识（如 "1" 表示网页微信，"2" 表示小程序等）
	 */
	String getType();

	/**
	 * 根据 openId 查询是否已有绑定用户
	 */
	SysUser selectUserByOpenId(String openId);

	/**
	 * 绑定社交账号到用户
	 * @param user 用户对象
	 * @param openId 第三方用户唯一标识
	 */
	void bindUser(SysUser user, String openId);

	/**
	 * 解绑用户对应社交账号
	 * @param user 要解绑的用户对象
	 * @return 是否成功
	 */
	boolean unbindUser(SysUser user);

}
