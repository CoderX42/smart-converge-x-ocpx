

package com.yzytech.scx.common.myabtis.shop;

/**
 * ShopContextHolder 工具类（用户端从请求头或 threadLocal 传入 shopId）
 *
 * @date 2025/8/5
 */
public class ShopContextHolder {

	private static final ThreadLocal<String> SHOP_ID_HOLDER = new ThreadLocal<>();

	public static void setShopId(String shopId) {
		SHOP_ID_HOLDER.set(shopId);
	}

	public static String getShopId() {
		return SHOP_ID_HOLDER.get();
	}

	public static void clear() {
		SHOP_ID_HOLDER.remove();
	}

}
