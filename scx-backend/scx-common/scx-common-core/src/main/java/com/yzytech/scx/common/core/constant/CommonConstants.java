
package com.yzytech.scx.common.core.constant;

/**
 * 公共常量
 *
 * @since 2022/2/10 12:00
 */
public interface CommonConstants {

	int SUCCESS = 0;

	int FAIL = 1;

	/**
	 * 系统状态：0.正常；1.停用
	 */
	String NORMAL_STATUS = "0";

	/**
	 * 顶级ID
	 */
	String PARENT_ID = "0";

	/**
	 * 菜单
	 */
	String MENU_TYPE_0 = "0";

	/**
	 * 按钮
	 */
	String MENU_TYPE_1 = "1";

	/**
	 * 文件存储类型1、阿里OSS；2、七牛云；3、腾讯云
	 */
	String STORAGE_TYPE_1 = "1";

	String STORAGE_TYPE_2 = "2";

	String STORAGE_TYPE_3 = "3";

	/**
	 * 登录状态：0.失败；1.成功；
	 */
	String LOGIN_LOG_STATUS_0 = "0";

	String LOGIN_LOG_STATUS_1 = "1";

	/**
	 * 公共状态：1.是；0.否；
	 */
	String YES = "1";

	String NO = "0";

	/**
	 * 管理员角色编码
	 */
	String ROLE_ADMIN_CODE = "ROLE_ADMIN";

	/**
	 * 店铺管理员角色编码
	 */
	String ROLE_SHOP_ADMIN_CODE = "ROLE_SHOP_ADMIN";

	/**
	 * 默认店铺管理员角色编码
	 */
	String DEFAULT_ROLE_SHOP_ADMIN_CODE = "DEFAULT_ROLE_SHOP_ADMIN";

	/**
	 * 店铺管理员角色名称
	 */
	String ROLE_SHOP_ADMIN_NAME = "店铺管理员";

	/**
	 * 管理员角色名称
	 */
	String ROLE_ADMIN_NAME = "系统管理员";

	/**
	 * 请求头店铺ID
	 */
	String SHOP_ID = "shop-id";

	/**
	 * token key
	 */
	String TOKEN_KEY = "satoken";

	/**
	 * 滑块验证码类型
	 */
	String IMAGE_CODE_TYPE_BP = "blockPuzzle";

	/**
	 * 文字点选验证码类型
	 */
	String IMAGE_CODE_TYPE_CW = "clickWord";

	// 默认部门ID
	String DEFAULT_DEPT_ID = "1";

	// 系统主账户
	String USER_TYPE_PRIMARY = "0";

	// 系统店铺账号
	String USER_TYPE_SHOP = "1";

	// 用户名注册类型
	String REGISTER_TYPE_USERNAME = "username";

	// 手机号注册类型
	String REGISTER_TYPE_PHONE = "phone";

	String UTF8 = "UTF-8";

}
