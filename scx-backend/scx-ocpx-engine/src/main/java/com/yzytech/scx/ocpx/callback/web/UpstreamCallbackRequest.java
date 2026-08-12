package com.yzytech.scx.ocpx.callback.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** {@code /callback} 接收的上游广告主转化参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpstreamCallbackRequest extends CallbackContextRequest {

	/** 转化动作编号，映射到 {@code ActionTypeEnum}。 */
	private Integer event_type;

	/** 转化发生时间戳。 */
	private Long event_time;

}
