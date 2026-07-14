

package com.yzytech.scx.pay.api.utils;

import java.math.BigDecimal;

public class WxPayHelper {

	public static int amountToFen(BigDecimal amount) {
		return amount.multiply(BigDecimal.valueOf(100)).intValue();
	}

	public static String buildNotifyUrl(String baseUrl, String prefix, String scene, String terminalType) {
		return String.format("%s/%s/%s/wx/%s", baseUrl, prefix, scene, terminalType);
	}

}
