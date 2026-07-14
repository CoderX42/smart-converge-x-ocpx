

package com.yzytech.scx.pay.handler;

import com.yzytech.scx.pay.api.dto.CreateRefundsReqDTO;
import com.yzytech.scx.pay.api.entity.PayRefundOrder;

public interface PayRefundOrderHandler {

	/**
	 * 创建退款单
	 * @param createRefundsReqDTO 退款参数
	 * @return obj
	 */
	PayRefundOrder createRefundOrder(CreateRefundsReqDTO createRefundsReqDTO);

	/**
	 * 调起渠道退款
	 * @param createRefundsReqDTO 退款参数
	 * @return obj
	 */
	Object refund(CreateRefundsReqDTO createRefundsReqDTO);

}
