package com.yzytech.scx.ocpx.constant.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;

public enum OsTypeEnum {

	ANDROID("ANDROID"), IOS("IOS"), UNKNOWN("UNKNOWN");

	private final String index;

	OsTypeEnum(String index) {
		this.index = index;
	}

	@JSONField(value = true)
	public String getIndex() {
		return index;
	}

	@JSONCreator
	public static OsTypeEnum getDeviceType(String index) {
		for (OsTypeEnum value : values()) {
			if (value.index.equals(index)) {
				return value;
			}
		}
		return null;
	}

}
