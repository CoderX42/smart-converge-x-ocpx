package com.yzytech.scx.ocpx.callback.model;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

	/** 合作方原始动作名称，用于协议忠实度审计。 */
	private String partnerActionType;

	/** AERIM purchase 原始十进制金额，不推断币种或换算单位。 */
	private BigDecimal currencyAmount;

	/**
	 * Build a detached callback snapshot for asynchronous media delivery.  RTA
	 * protected feedback must never let the worker retain the inbound object (which
	 * may contain raw identifiers); {@link FeedbackReq#copyForRtaDownstream()} keeps
	 * only hashed/context fields and a callback URL that passed the raw-value scan.
	 */
	public CallbackReq copyForRtaDownstream() {
		FeedbackReq snapshot = feedback != null && feedback.isRtaProtected()
				? feedback.copyForRtaDownstream() : feedback;
		return CallbackReq.builder()
			.feedback(snapshot)
			.actionType(actionType)
			.actionTime(actionTime)
			.partnerActionType(partnerActionType)
			.currencyAmount(currencyAmount)
			.build();
	}

}
