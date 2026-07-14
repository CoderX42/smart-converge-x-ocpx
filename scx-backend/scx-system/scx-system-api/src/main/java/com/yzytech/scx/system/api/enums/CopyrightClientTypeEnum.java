

package com.yzytech.scx.system.api.enums;

public enum CopyrightClientTypeEnum {

	USER("1", "用户端"), MERCHANT("2", "商户端"), PLATFORM("3", "平台端");

	private final String code;

	private final String label;

	CopyrightClientTypeEnum(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public static CopyrightClientTypeEnum fromCode(String code) {
		for (CopyrightClientTypeEnum type : values()) {
			if (type.code.equals(code)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Invalid client type code: " + code);
	}

}
