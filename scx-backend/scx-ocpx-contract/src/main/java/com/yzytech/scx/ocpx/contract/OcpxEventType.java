package com.yzytech.scx.ocpx.contract;

import java.util.Locale;

/**
 * 事件枚举值
 */
public enum OcpxEventType {

	SHOW,

	CLICK,

	ACTIVATE,

	FIRST_ACTIVATION,

	EVOKE,

	LOGIN,

	REGISTER,

	NEXT_DAY_RETENTION,

	RETENTION_D2,

	RETENTION_D3,

	RETENTION_D4,

	RETENTION_D5,

	RETENTION_D6,

	RETENTION_D7,

	RETENTION,

	PAYMENT,

	REACTIVATION,

	CUSTOMER_ACQUISITION,

	VISIT,

	AUTHENTICATION,

	ORDER,

	FIRST_ORDER,

	FIRST_REACTIVATION,

	COMPLETE_ORDER,

	VIDEO_COMPLETE_90S,

	KEY_ACTION,

	FORM_SUBMIT,

	KEY_PAGE_VIEW,

	RECALL,

	UNKNOWN;

	public static OcpxEventType fromConversionCode(String value) {
		if (value == null || value.isBlank()) {
			return UNKNOWN;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ignored) {
			return UNKNOWN;
		}
	}

	public static OcpxEventType fromExternalOrUnknown(String value) {
		try {
			return fromExternal(value);
		}
		catch (IllegalArgumentException ignored) {
			return UNKNOWN;
		}
	}

	public static OcpxEventType fromExternal(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("event is required");
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "show", "impression" -> SHOW;
			case "click" -> CLICK;
			case "activate", "active" -> ACTIVATE;
			case "firstactivation", "first_activation", "first_activate" -> FIRST_ACTIVATION;
			case "evoke", "launch", "evoke_c", "evoke_valid" -> EVOKE;
			case "login" -> LOGIN;
			case "register" -> REGISTER;
			case "day1retention", "next_day_retention", "next_stay" -> NEXT_DAY_RETENTION;
			case "retention_d3", "day3retention", "retention3" -> RETENTION_D3;
			case "retention_d4", "day4retention", "retention4" -> RETENTION_D4;
			case "retention_d5", "day5retention", "retention5" -> RETENTION_D5;
			case "retention_d6", "day6retention", "retention6" -> RETENTION_D6;
			case "retention_d7", "day7retention", "retention7" -> RETENTION_D7;
			case "retention", "recall" -> RETENTION;
			case "purchase", "payment", "pay" -> PAYMENT;
			case "reactivation", "lahuo", "lahui", "huifang" -> REACTIVATION;
			case "new", "customer_acquisition" -> CUSTOMER_ACQUISITION;
			case "visit", "visits" -> VISIT;
			case "authentication", "authenticate" -> AUTHENTICATION;
			case "order", "place_order" -> ORDER;
			case "firstorder", "first_order", "first_purchase" -> FIRST_ORDER;
			case "firstreactivation", "first_reactivation", "evoke_first" -> FIRST_REACTIVATION;
			case "completeorder", "complete_order", "finish_order" -> COMPLETE_ORDER;
			default -> throw new IllegalArgumentException("unsupported event: " + value);
		};
	}

}
