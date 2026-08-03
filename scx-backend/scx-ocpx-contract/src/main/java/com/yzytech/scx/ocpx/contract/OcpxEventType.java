package com.yzytech.scx.ocpx.contract;

import java.util.Locale;

public enum OcpxEventType {

	SHOW,

	CLICK,

	ACTIVATE,

	REGISTER,

	NEXT_DAY_RETENTION,

	RETENTION,

	PAYMENT,

	REACTIVATION,

	CUSTOMER_ACQUISITION,

	FIRST_REACTIVATION,

	COMPLETE_ORDER;

	public static OcpxEventType fromExternal(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("event is required");
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "show", "impression" -> SHOW;
			case "click" -> CLICK;
			case "activate" -> ACTIVATE;
			case "register" -> REGISTER;
			case "day1retention", "next_day_retention", "next_stay" -> NEXT_DAY_RETENTION;
			case "retention", "recall" -> RETENTION;
			case "purchase", "payment", "pay" -> PAYMENT;
			case "reactivation" -> REACTIVATION;
			case "new", "customer_acquisition" -> CUSTOMER_ACQUISITION;
			case "firstreactivation", "first_reactivation" -> FIRST_REACTIVATION;
			case "completeorder", "complete_order", "finish_order" -> COMPLETE_ORDER;
			default -> throw new IllegalArgumentException("unsupported event: " + value);
		};
	}

}
