

package com.yzytech.scx.common.core.util;

import org.springframework.util.StringUtils;

public class ConvertUtils {

	public static String clientToChannel(String clientType) {
		if (!StringUtils.hasText(clientType)) {
			return null;
		}
		return switch (clientType) {
			case "WX_MA" -> "1";
			case "H5" -> "2";
			case "APP" -> "3";
			case "WX_H5" -> "4";
			case "PC" -> "5";
			default -> "";
		};
	}

	public static String limitDescription(String description) {
		if (description == null) {
			return "";
		}
		// 去掉前后空格
		description = description.trim();
		// 截取前 127 个字符
		if (description.length() > 127) {
			return description.substring(0, 127);
		}
		return description;
	}

}
