package com.yzytech.scx.ocpx.shared.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 广告主 Callback 动作类型。
 *
 * <p>
 * 编号是外部协议字段，不能按枚举声明顺序重新编号。扣量状态覆盖 1..19；12..17 为旧协议保留项，18/19 为首活与首购。
 */
public enum ActionTypeEnum {

	ACTIVATE(1), EVOKE(2), LOGIN(3), REGISTER(4), PAYMENT(5), CUSTOMER_ACQUISITION(6), NEXT_STAY(7), VISITS(8),
	AUTHENTICATION(9), ORDER(10), FINISH_THE_ORDER(11), EVOKE_C(12), EVOKE_FIRST(13), EVOKE_C_VALID(14), LAHUO(15),
	LAHUI(16), HUIFANG(17), FIRST_ACTIVATION(18), FIRST_ORDER(19);

	private final Integer index;

	ActionTypeEnum(Integer index) {
		this.index = index;
	}

	@JSONField(value = true)
	public Integer getIndex() {
		return index;
	}

	/**
	 * 按外部协议编号解析动作类型。
	 * @param index Callback 中的动作编号
	 * @return 匹配的动作；未知编号返回 {@code null}
	 */
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
