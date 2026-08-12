package com.yzytech.scx.ocpx.shared.constant;

import com.yzytech.scx.ocpx.core.OcpxRefreshContract;

/** OCPX Engine 沿用的 Redis Key 前缀。 */
public final class RedisConstants {

	/** Feedback 去重 Hash 前缀，后接固定分桶号。 */
	public static final String REPEAT_REPORT = "r:u:rpe:";

	/** Callback 去重 Hash 前缀，后接固定分桶号。 */
	public static final String REPEAT_CALLBACK_REPORT = "r:cl:rpe:";

	/** Feedback 报表待落库 List 前缀，后接工作分桶号。 */
	public static final String DATA_REPORT = "r:da:report:";

	/** Callback 报表待落库 List 前缀，后接工作分桶号。 */
	public static final String DATA_REPORT_CALLBACK = "r:da:report:cb:";

	/** Admin 与 Engine 共享的配置刷新标记。 */
	public static final String DATA_REFRESH = OcpxRefreshContract.REDIS_KEY;

	/** 推广链接与媒体组合的目标回传比例 Key 前缀。 */
	public static final String ROUTE_RATIO_PREFIX = "ocpx:route:ratio:";

	/** 推广链接与媒体组合的上游原始转化计数 Key 前缀。 */
	public static final String ROUTE_ORIGINAL_COUNT_PREFIX = "ocpx:route:original-count:";

	/** 推广链接与媒体组合的实际下游回传计数 Key 前缀。 */
	public static final String ROUTE_DELIVERED_COUNT_PREFIX = "ocpx:route:delivered-count:";

	private RedisConstants() {
	}

}
