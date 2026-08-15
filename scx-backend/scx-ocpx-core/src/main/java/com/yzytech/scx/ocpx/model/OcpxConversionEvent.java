package com.yzytech.scx.ocpx.model;

import java.util.Arrays;

/**
 * 监测任务可选择的目标转化事件。
 *
 * <p>枚举名称与 Engine {@code ActionTypeEnum} 的公开事件代码保持一致，便于在事件接收时冻结目标转化口径。</p>
 */
public enum OcpxConversionEvent {

	ACTIVATE,

	EVOKE,

	LOGIN,

	REGISTER,

	PAYMENT,

	CUSTOMER_ACQUISITION,

	NEXT_STAY,

	VISITS,

	AUTHENTICATION,

	ORDER,

	FINISH_THE_ORDER,

	FIRST_ACTIVATION,

	FIRST_ORDER;

	public static boolean supports(String value) {
		return value != null && Arrays.stream(values()).anyMatch(item -> item.name().equals(value));
	}

}
