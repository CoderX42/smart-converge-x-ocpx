package com.yzytech.scx.delivery.entity;

import lombok.Getter;

/**
 * 接入文档类型枚举。 限定 scx_integration_document.type 的合法取值，防止自由文本录入不规范。
 */
@Getter
public enum IntegrationDocumentType {

	/** SCX 主动回传转化结果给媒体的协议。 */
	MEDIA_CALLBACK("媒体回传协议"),

	/** 媒体/DSP 上报点击、曝光的接口规范。 */
	MEDIA_API("媒体接口规范"),

	/** 上游/广告主上报转化事件的接口规范。 */
	PRODUCT_API("产品接口规范"),

	/** RTA 实时投放决策协议。 */
	RTA_SPEC("RTA 决策协议"),

	/** 其他未分类接入文档。 */
	OTHER("其他");

	private final String label;

	IntegrationDocumentType(String label) {
		this.label = label;
	}

	/** 从字符串解析枚举值，不区分大小写。非法值返回 null。 */
	public static IntegrationDocumentType fromString(String value) {
		if (value == null || value.isBlank())
			return null;
		try {
			return valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 判断给定的 type 字符串是否为合法枚举值。 */
	public static boolean isValid(String value) {
		return fromString(value) != null;
	}

}
