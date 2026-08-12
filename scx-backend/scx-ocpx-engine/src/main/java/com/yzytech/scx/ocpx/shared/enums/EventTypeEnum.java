package com.yzytech.scx.ocpx.shared.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;

/** 媒体 Feedback 事件类型及其外部协议代码。 */
public enum EventTypeEnum {

	WIN("WIN"), IMP("IMP"), CLICK("CLK"), CLOSE("CLOSE"), DP_START("DP_START"), DP_SUCC("DP_SUCC"), DP_FAIL("DP_FAIL"),
	DOWN_START("DOWN_START"), DOWN_END("DOWN_END"), INSTALL_START("INSTALL_START"), INSTALL_END("INSTALL_END"),
	VIDEO_START("VIDEO_START"), VIDEO_END("VIDEO_END"), VIDEO_CLICK("VIDEO_CLICK"), VIDEO_CLOSE("VIDEO_CLOSE"),
	VIDEO_SKIP("VIDEO_SKIP"), VIDEO_MUTE("VIDEO_MUTE"), VIDEO_UNMUTE("VIDEO_UNMUTE"), VIDEO_PAUSE("VIDEO_PAUSE"),
	VIDEO_REPLAY("VIDEO_REPLAY"), VIDEO_FULL("VIDEO_FULL"), VIDEO_UNFULL("VIDEO_UNFULL"),
	VIDEO_LOAD_SUCCESS("VIDEO_LOAD_SUCCESS"), VIDEO_LOAD_FAIL("VIDEO_LOAD_FAIL"),
	VIDEO_ONE_QUARTER("VIDEO_ONE_QUARTER"), VIDEO_HALF("VIDEO_HALF"), VIDEO_THREE_QUARTER("VIDEO_THREE_QUARTER");

	private final String key;

	EventTypeEnum(String key) {
		this.key = key;
	}

	@JSONField(value = true)
	public String getKey() {
		return key;
	}

	/**
	 * 按外部协议代码解析事件。
	 * @param event 媒体传入的事件代码
	 * @return 匹配事件；未知代码返回 {@code null}
	 */
	@JSONCreator
	public static EventTypeEnum getEventTypeEnum(String event) {
		for (EventTypeEnum value : values()) {
			if (value.key.equals(event)) {
				return value;
			}
		}
		return null;
	}

}
