package com.yzytech.scx.ocpx.callback.service;

/** Callback 经过校验和扣量计算后的处置结果。 */
public enum CallbackDecision {

	/** 请求无有效动作或为重复请求，无需继续处理。 */
	NOT_APPLICABLE,

	/** 已提交下游媒体异步回传任务；不代表媒体已经接收。 */
	FORWARDED,

	/** 已计入原始转化，但按扣量比例不向媒体回传。 */
	DEDUCTED

}
