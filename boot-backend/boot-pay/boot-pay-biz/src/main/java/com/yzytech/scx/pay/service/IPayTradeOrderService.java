

package com.yzytech.scx.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;

/**
 * 支付订单
 *
 * @date 2022/6/16
 */
public interface IPayTradeOrderService extends IService<PayTradeOrder> {

	boolean closePayOrder(String mergeOrderNo, String shopId);

}
