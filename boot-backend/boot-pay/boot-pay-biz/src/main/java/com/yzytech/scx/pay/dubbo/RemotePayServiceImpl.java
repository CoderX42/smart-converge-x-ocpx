

package com.yzytech.scx.pay.dubbo;

import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;
import com.yzytech.scx.pay.api.remote.RemotePayService;
import com.yzytech.scx.pay.handler.PayOrderHandler;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemotePayServiceImpl implements RemotePayService {

	private final IPayTradeOrderService payTradeOrderService;

	private final Map<String, PayOrderHandler> payOrderHandlerMap;

	@Override
	public Object createOrder(CreateOrderReqDTO createOrderReqDTO) {
		return payOrderHandlerMap.get(createOrderReqDTO.getTradeType()).pay(createOrderReqDTO);
	}

	@Override
	public boolean closePayOrder(String mergeOrderNo, String shopId) {
		return payTradeOrderService.closePayOrder(mergeOrderNo, shopId);
	}

}
