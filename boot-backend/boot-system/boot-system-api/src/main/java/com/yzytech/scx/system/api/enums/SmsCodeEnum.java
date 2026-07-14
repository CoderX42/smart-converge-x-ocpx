

package com.yzytech.scx.system.api.enums;

public enum SmsCodeEnum {

	BASE_CODE("BASE_CODE", "登录短信配置");

	private final String code;

	private final String description;

	SmsCodeEnum(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	// 根据 code 获取枚举
	public static SmsCodeEnum fromCode(String code) {
		for (SmsCodeEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		return null;
	}

}
