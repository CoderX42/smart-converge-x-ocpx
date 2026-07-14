

package com.yzytech.scx.common.core.constant;

/**
 * 缓存常量
 *
 * @since 2022/2/18 17:00
 */
public interface CacheConstants {

	/** 验证码key */
	String DEFAULT_CODE_KEY = "random_code_";

	/** 短信验证码key */
	String SMS_CODE_KEY = "sms_code_";

	/** 用户信息缓存 */
	String USER_CACHE = "user_cache";

	/** 系统在线用户 */
	String SYS_ONLINE_KEY = "satoken:login:online_user:";

	/** 菜单信息缓存 */
	String MENU_CACHE = "menu_cache";

	/** 字典缓存 */
	String DICT_CACHE = "dict_cache";

	/** 微信应用缓存 */
	String WX_APP_CACHE = "wx_app_cache";

	/** 首页页面设计缓存 */
	String HOME_PAGE_DESIGN_CACHE = "home_page_design_cache:";

	/** 首页页面设计锁key */
	String HOME_PAGE_DESIGN_LOCK_CACHE = HOME_PAGE_DESIGN_CACHE + "lock:";

	/** 移动端主题缓存 */
	String MOBILE_THEME_CACHE = "mobile_theme_cache:";

	/** 移动端主题缓存锁key */
	String MOBILE_THEME_LOCK_CACHE = MOBILE_THEME_CACHE + "lock:";

	String ORDER_CONFIG_CACHE = "order_config_cache:";

	String GOODS_SKU_STOCK = "goods:sku:stock:";

	String GOODS_SPU_STOCK = "goods:spu:stock:";

	/** 秒杀活动维度库存（与 DB seckill_activity.stock 对应） */
	String SECKILL_ACTIVITY_STOCK = "seckill:activity:stock:";

	/** 秒杀活动下 SKU 库存（与 DB seckill_goods.stock 对应），完整 key：前缀 + activityId + ':' + skuId */
	String SECKILL_SKU_STOCK = "seckill:sku:stock:";

	/** 用户余额锁key */
	String USER_BALANCE_LOCK = "user:balance:lock:";

	/** 用户分销佣金锁key */
	String USER_DISTRIBUTION_LOCK = "user:distribution:lock:";

}
