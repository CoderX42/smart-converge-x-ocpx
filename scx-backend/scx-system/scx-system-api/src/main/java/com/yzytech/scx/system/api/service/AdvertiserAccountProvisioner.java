package com.yzytech.scx.system.api.service;

/**
 * 广告主登录账号开户服务。
 *
 * <p>
 * 调用方负责在同一数据库事务中创建广告主；实现必须加入该事务，确保广告主与登录账号原子落库。
 * </p>
 */
public interface AdvertiserAccountProvisioner {

	/**
	 * 创建广告主登录账号并绑定固定广告主角色。
	 * @param advertiserId 广告主主键
	 * @param advertiserName 广告主名称
	 * @param username 登录用户名
	 * @param rawPassword 原始密码，仅用于本次编码，不得记录日志或返回
	 * @return 已创建账号的非敏感标识
	 */
	ProvisionedAccount provision(String advertiserId, String advertiserName, String username, String rawPassword);

	/** 已创建账号的非敏感结果。 */
	record ProvisionedAccount(String userId, String username) {
	}

}
