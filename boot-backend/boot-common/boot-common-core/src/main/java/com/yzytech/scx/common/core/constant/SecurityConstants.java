

package com.yzytech.scx.common.core.constant;

/**
 * 安全相关常量
 *
 * @since 2022/2/17 15:39
 */
public interface SecurityConstants {

	/**
	 * 登录URL
	 */
	String OAUTH_TOKEN = "/token/login";

	/**
	 * 短信验证码登录URL
	 */
	String PHONE_SMS_TOKEN = "/token/sms/login";

	/**
	 * TOC短信验证码登录URL
	 */
	String TOC_PHONE_SMS_TOKEN = "/toc-token/sms/login";

	/**
	 * 注册
	 */

	/**
	 * 来源
	 */
	String SOURCE = "source";

	/**
	 * 系统内部
	 */
	String SOURCE_IN = "source_in";

	/**
	 * 系统外部
	 */
	String SOURCE_OUTER = "source_outer";

	/**
	 * 微信网页授权
	 */
	String WX_AUTH_URL = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";

	/**
	 * 微信小程序授权
	 */
	String WX_MINI_AUTH_URL = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

}
