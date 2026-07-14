

package com.yzytech.scx.pay.api.remote;

import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;

/**
 */
public interface RemotePayService {

	Object createOrder(CreateOrderReqDTO createOrderReqDTO);

	/**
	 * 取消支付订单
	 * @param mergeOrderNo 订单ID
	 * @param shopId 店铺ID
	 * @return
	 */
	boolean closePayOrder(String mergeOrderNo, String shopId);

}
