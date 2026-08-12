package com.yzytech.scx.ocpx.callback.model;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Engine 内部统一的转化回调模型。
 *
 * <p>
 * {@code feedback} 来自上游回调参数 {@code ext} 的解密结果，用于恢复媒体、任务、链接和原回调地址。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackReq {

	/** 发起正向 Feedback 时保存的完整请求上下文。 */
	private FeedbackReq feedback;

	/** 上游广告主报告的转化动作。 */
	private ActionTypeEnum actionType;

	/** 上游传入的动作发生时间戳。 */
	private Long actionTime;

}
