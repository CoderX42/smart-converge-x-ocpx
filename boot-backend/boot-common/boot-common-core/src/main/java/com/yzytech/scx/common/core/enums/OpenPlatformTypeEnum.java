

package com.yzytech.scx.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户端类型
 *
 * @since 2022/3/10 14:10
 */
@Getter
@AllArgsConstructor
public enum OpenPlatformTypeEnum {

	/** 微信小程序 */
	WX_MA("WX_MA", "微信小程序"),
	/** 支付宝小程序 */
	ALI_MA("ALI_MA", "支付宝小程序"),
	/** 微信公众号H5 */
	WX_H5("WX_H5", "微信公众号H5"),
	/** 普通H5 */
	H5("H5", "普通H5"),
	/** APP */
	APP("APP", "APP");

	/** code */
	private final String code;

	/** 描述 */
	private final String desc;

	/**
	 * 自己定义一个静态方法,通过code返回枚举描述
	 *
	 * @date 2022/6/16
	 * @param code
	 * @return: java.lang.String
	 */
	public static String getValue(String code) {

		for (OpenPlatformTypeEnum openPlatformTypeEnum : values()) {
			if (openPlatformTypeEnum.getCode().equals(code)) {
				return openPlatformTypeEnum.getDesc();
			}
		}
		return null;
	}

}
