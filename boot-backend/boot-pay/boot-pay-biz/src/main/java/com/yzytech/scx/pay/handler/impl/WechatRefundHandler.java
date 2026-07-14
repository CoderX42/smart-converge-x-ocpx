

package com.yzytech.scx.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.yzytech.scx.common.core.entity.CallbackPrefixProperties;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.dto.CreateRefundsReqDTO;
import com.yzytech.scx.pay.api.entity.PayRefundOrder;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.enums.PayRefundOrderStatusEnum;
import com.yzytech.scx.pay.config.WxPayConfiguration;
import com.yzytech.scx.pay.handler.AbstractPayRefundOrderHandler;
import com.yzytech.scx.pay.service.IPayRefundOrderService;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Service("WECHAT_REFUND")
@RequiredArgsConstructor
public class WechatRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final IPayTradeOrderService tradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	@Override
	public Object doRefund(PayRefundOrder payRefundOrder) {

		WxPayService wxPayService = WxPayConfiguration.wxPayService(payRefundOrder.getTerminalType());

		String notifyUrl = String.format("%s/%s/notify/refunds/wx/%s/%s", payRefundOrder.getNotifyUrl(),
				callbackPrefixProperties.getPay(), null,
				payRefundOrder.getTerminalType());

		WxPayRefundV3Request.Amount amount = new WxPayRefundV3Request.Amount();
		amount.setTotal(payRefundOrder.getPayAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setRefund(payRefundOrder.getRefundAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setCurrency("CNY");
		WxPayRefundV3Request wxPayRefundV3Request = new WxPayRefundV3Request();
		wxPayRefundV3Request.setAmount(amount);
		wxPayRefundV3Request.setOutTradeNo(payRefundOrder.getOutTradeNo());
		wxPayRefundV3Request.setOutRefundNo(payRefundOrder.getRefundTradeNo());
		wxPayRefundV3Request.setNotifyUrl(notifyUrl);
		if (StringUtils.hasText(payRefundOrder.getSubMchid())) {
			wxPayRefundV3Request.setSubMchid(payRefundOrder.getSubMchid());
		}
		try {
			return wxPayService.refundV3(wxPayRefundV3Request);
		}
		catch (WxPayException e) {
			throw new BootBusinessException(e.getReturnMsg());
		}
	}

	@Override
	public PayRefundOrder createRefundOrder(CreateRefundsReqDTO createRefundsReqDTO) {
		PayRefundOrder payRefundOrder = payRefundOrderService.getOne(Wrappers.<PayRefundOrder>lambdaQuery()
			.eq(PayRefundOrder::getChannelRefundNo, createRefundsReqDTO.getRefundTradeNo()));
		if (Objects.nonNull(payRefundOrder)) {
			return payRefundOrder;
		}
		// 查询支付单
		PayTradeOrder payTradeOrder = tradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createRefundsReqDTO.getOutTradeNo()));
		if (Objects.isNull(payTradeOrder)) {
			throw new IllegalArgumentException("支付单不存在");
		}

		String subMchid = "";
		if (PayConstants.PAY_MODE_SERVICE_PROVIDER.equals(payTradeOrder.getPayMode())) {
			subMchid = payTradeOrder.getWxSubMchid();
		}

		payRefundOrder = new PayRefundOrder();
		payRefundOrder.setRefundAmount(createRefundsReqDTO.getRefundAmount());
		payRefundOrder.setPayAmount(createRefundsReqDTO.getTotalAmount());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_1.getCode());
		payRefundOrder.setRefundTradeNo(createRefundsReqDTO.getRefundTradeNo());
		payRefundOrder.setNotifyUrl(createRefundsReqDTO.getNotifyUrl());
		payRefundOrder.setOutTradeNo(createRefundsReqDTO.getOutTradeNo());
		payRefundOrder.setExtra(createRefundsReqDTO.getExtra());
		payRefundOrder.setSubMchid(subMchid);
		payRefundOrder.setTerminalType(payTradeOrder.getTerminalType());
		payRefundOrderService.save(payRefundOrder);
		return payRefundOrder;
	}

}
