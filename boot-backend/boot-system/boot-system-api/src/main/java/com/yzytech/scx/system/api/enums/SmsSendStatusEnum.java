

package com.yzytech.scx.system.api.enums;

public enum SmsSendStatusEnum {

	WAITING("0", "待发送"), SUCCESS("1", "发送成功"), FAIL("2", "发送失败");

	private final String code;

	private final String description;

	SmsSendStatusEnum(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public static SmsSendStatusEnum fromCode(String code) {
		for (SmsSendStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		return null;
	}

}
