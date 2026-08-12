package com.yzytech.scx.ocpx.callback.service;

/** Callback 经过校验和扣量计算后的处置结果。 */
public enum CallbackDecision {

	/** 请求无有效动作或为重复请求，无需继续处理。 */
	NOT_APPLICABLE,

	/** 已向下游媒体回传。 */
	FORWARDED,

	/** 已计入原始转化，但按扣量比例不向媒体回传。 */
	DEDUCTED

}
