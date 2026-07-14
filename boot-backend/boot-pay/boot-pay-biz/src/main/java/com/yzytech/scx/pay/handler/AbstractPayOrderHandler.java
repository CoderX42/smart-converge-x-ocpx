

package com.yzytech.scx.pay.handler;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.utils.PayChannelContext;
import com.yzytech.scx.pay.api.vo.PayConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 支付抽象类
 *
 * @since 2023/4/18 19:11
 */
@Slf4j
public abstract class AbstractPayOrderHandler implements PayOrderHandler {

	@Override
	public Object pay(CreateOrderReqDTO createOrderReqDTO) {
		// 参数检查
		validateRequest(createOrderReqDTO);
		// 构建支付参数
		PayConfigVO payConfig = preParams(createOrderReqDTO);
		if (Objects.nonNull(payConfig)) {
			// 判断入参appid和支付配置appid
			if (StringUtils.hasText(createOrderReqDTO.getAppId())
					&& !createOrderReqDTO.getAppId().equals(payConfig.getAppId())) {
				payConfig.setAppId(createOrderReqDTO.getAppId());
			}
			PayChannelContext.setChannel(payConfig);
			if (payConfig.getMode().equals(PayConstants.PAY_MODE_SERVICE_PROVIDER)
					&& createOrderReqDTO.getOrderNum() > 1) {
				throw new BootBusinessException(9901, "服务商模式多店铺下单需要分笔支付");
			}
		}

		// 创建订单
		PayTradeOrder payTradeOrder = createOrder(createOrderReqDTO);
		// 支付逻辑
		Object result = doPay(payTradeOrder);
		// 后置处理
		postPay(payTradeOrder);
		return result;
	}

	public abstract Object doPay(PayTradeOrder payTradeOrder);

	private void validateRequest(CreateOrderReqDTO createOrderReqDTO) {
		// 参数检查
	}

	private void postPay(PayTradeOrder payTradeOrder) {
		// 后置处理
		PayChannelContext.clear();
	}

}
