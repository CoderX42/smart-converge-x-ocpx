

package com.yzytech.scx.common.core.util;

import cn.hutool.core.util.IdUtil;

/**
 * 全局唯一ID工具类
 *
 * @since 2022/3/14 16:24
 */
public class SnowflakeIdUtils {

	private final static String MERGE_ORDER_NO_PREFIX = "MP";

	/**
	 * 订单编号
	 *
	 * @date 2022/7/2
	 * @return: java.lang.String
	 */
	public static String orderNo() {
		return IdUtil.getSnowflake(1, 1).nextIdStr();
	}

	/**
	 * 聚合订单编号
	 *
	 * @date 2022/7/2
	 * @return: java.lang.String
	 */
	public static String mergeOrderNo() {
		return MERGE_ORDER_NO_PREFIX + IdUtil.getSnowflake(2, 1).nextIdStr();
	}

	/**
	 * 退款单号
	 *
	 * @date 2022/7/2
	 * @return: java.lang.String
	 */
	public static String refundOrderNo() {
		return IdUtil.getSnowflake(1, 2).nextIdStr();
	}

	/**
	 * 余额充值单号
	 *
	 * @date 2024/12/6
	 * @return: java.lang.String
	 */
	public static String balanceRechargeOrderNo() {
		return IdUtil.getSnowflake(1, 3).nextIdStr();
	}

}
