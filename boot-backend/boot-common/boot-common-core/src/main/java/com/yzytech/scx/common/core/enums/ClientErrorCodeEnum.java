package com.yzytech.scx.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ClientErrorCodeEnum {

	UNKNOWN("UNKNOWN", "未知错误");

	private final String code;

	private final String message;

}
