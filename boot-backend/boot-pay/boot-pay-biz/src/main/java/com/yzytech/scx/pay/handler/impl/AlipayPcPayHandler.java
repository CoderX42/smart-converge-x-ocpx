

package com.yzytech.scx.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.entity.CallbackPrefixProperties;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.enums.PayTerminalTypeEnum;
import com.yzytech.scx.pay.api.enums.PayTradeTypeEnum;
import com.yzytech.scx.pay.api.utils.PayChannelContext;
import com.yzytech.scx.pay.api.vo.PayConfigVO;
import com.yzytech.scx.pay.config.AliPayConfiguration;
import com.yzytech.scx.pay.handler.AbstractPayOrderHandler;
import com.yzytech.scx.pay.service.IPayConfigService;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;

@Service("ALI_PC_PAY")
@RequiredArgsConstructor
public class AlipayPcPayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final IPayConfigService payConfigService;

	@Override
	public Object doPay(PayTradeOrder payTradeOrder) {
		try {
			String notifyUrl = String.format("%s/%s/notify/alipay/%s/%s", payTradeOrder.getNotifyUrl(),
					callbackPrefixProperties.getPay(), null,
					payTradeOrder.getTerminalType());
			AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(payTradeOrder.getTerminalType());
			JSONObject bizContent = new JSONObject();
			/****** 必传参数 ******/
			bizContent.set("out_trade_no", payTradeOrder.getOutTradeNo());
			bizContent.set("total_amount", payTradeOrder.getAmount());
			bizContent.set("subject", payTradeOrder.getDescription());
			bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");
			AlipayTradePagePayRequest alipayTradePagePayRequest = new AlipayTradePagePayRequest();
			// 异步接收地址，仅支持http/https，公网可访问
			alipayTradePagePayRequest.setNotifyUrl(notifyUrl);
			// 同步跳转地址，仅支持http/https
			alipayTradePagePayRequest.setReturnUrl(payTradeOrder.getReturnUrl());
			// 用户付款中途退出返回商户网站的地址
			bizContent.set("request_from_url", payTradeOrder.getQuitUrl());
			if (StringUtils.hasText(payTradeOrder.getAlipaySubMchid())) {
				alipayTradePagePayRequest.putOtherTextParam("app_auth_token", payTradeOrder.getAlipaySubMchid());
			}
			alipayTradePagePayRequest.setBizContent(bizContent.toString());
			AlipayTradePagePayResponse alipayTradePagePayResponse = alipayClient.pageExecute(alipayTradePagePayRequest,
					"get");
			if (!alipayTradePagePayResponse.isSuccess()) {
				throw new BootBusinessException(
						alipayTradePagePayResponse.getMsg() + alipayTradePagePayResponse.getSubMsg());
			}
			return alipayTradePagePayResponse;
		}
		catch (Exception e) {
			throw new BootBusinessException(e.getMessage());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO dto) {
		return Optional.ofNullable(findExistOrder(dto)).orElseGet(() -> buildNewOrder(dto));
	}

	private PayTradeOrder findExistOrder(CreateOrderReqDTO dto) {
		return payTradeOrderService
			.getOne(Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, dto.getOutTradeNo()));
	}

	private PayTradeOrder buildNewOrder(CreateOrderReqDTO dto) {
		PayTradeOrder order = new PayTradeOrder();
		order.setPayStatus(CommonConstants.NO);
		order.setDescription(dto.getSubject());
		order.setOpenId(dto.getBuyerId());
		order.setOutTradeNo(dto.getOutTradeNo());
		order.setTradeType(PayTradeTypeEnum.ALI_PC_PAY.getName());
		order.setAmount(new BigDecimal(dto.getTotalAmount()));
		order.setNotifyUrl(dto.getNotifyUrl());
		order.setExtra(dto.getExtra());
		order.setTerminalType(PayTerminalTypeEnum.PC.getCode());
		order.setPayMode(PayChannelContext.getChannel().getMode());
		order.setAlipaySubMchid(dto.getAlipaySubMchid());
		order.setReturnUrl(dto.getReturnUrl());
		order.setQuitUrl(dto.getQuitUrl());
		order.setProfitSharingStatus(dto.getProfitSharingStatus());
		order.setUserId(dto.getUserId());
		payTradeOrderService.save(order);
		return order;
	}

	@Override
	public PayConfigVO preParams(CreateOrderReqDTO createOrderReqDTO) {
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_2, PayTerminalTypeEnum.PC.getCode());
		if (null == payConfig) {
			throw new IllegalArgumentException("未配置支付宝支付");
		}
		return payConfig;
	}

}
