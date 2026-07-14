

package com.yzytech.scx.system.api.enums;

public enum AgreementTypeEnum {

	SERVICE("1", "服务协议"), PRIVACY("2", "隐私协议"), DISTRIBUTION("3", "分销协议");

	private final String code;

	private final String label;

	AgreementTypeEnum(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public static AgreementTypeEnum fromCode(String code) {
		for (AgreementTypeEnum type : values()) {
			if (type.code.equals(code)) {
				return type;
			}
		}
		throw new IllegalArgumentException("未知协议类型 code: " + code);
	}

}
