package com.yzytech.scx.ocpx.constant.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;

public enum ActionTypeEnum {

	ACTIVATE(1), EVOKE(2), LOGIN(3), REGISTER(4), PAYMENT(5), CUSTOMER_ACQUISITION(6), NEXT_STAY(7), VISITS(8),
	AUTHENTICATION(9), ORDER(10), FINISH_THE_ORDER(11), EVOKE_C(12), EVOKE_FIRST(13), EVOKE_C_VALID(14), LAHUO(15),
	LAHUI(16), HUIFANG(17);

	private final Integer index;

	ActionTypeEnum(Integer index) {
		this.index = index;
	}

	@JSONField(value = true)
	public Integer getIndex() {
		return index;
	}

	@JSONCreator
	public static ActionTypeEnum getActionTypeEnum(Integer index) {
		for (ActionTypeEnum value : values()) {
			if (value.index.equals(index)) {
				return value;
			}
		}
		return null;
	}

}
