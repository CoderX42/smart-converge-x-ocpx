package com.yzytech.scx.ocpx.shared.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;

/** 标准化设备操作系统类型。 */
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

	/**
	 * 按枚举协议值解析操作系统。
	 * @param index 协议值，如 {@code ANDROID} 或 {@code IOS}
	 * @return 匹配类型；未知值返回 {@code null}
	 */
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
