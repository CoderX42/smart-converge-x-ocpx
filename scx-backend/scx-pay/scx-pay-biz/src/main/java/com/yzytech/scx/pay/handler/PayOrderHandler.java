

package com.yzytech.scx.pay.handler;

import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.vo.PayConfigVO;

public interface PayOrderHandler {

	/**
	 * 创建订单
	 * @param createOrderReqDTO 预支付参数
	 * @return obj
	 */
	PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO);

	/**
	 * 构建支付参数
	 */
	PayConfigVO preParams(CreateOrderReqDTO createOrderReqDTO);

	/**
	 * 调起渠道支付
	 * @param createOrderReqDTO 预支付参数
	 * @return obj
	 */
	Object pay(CreateOrderReqDTO createOrderReqDTO);

}
