

package com.yzytech.scx.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
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

@Service("ALI_H5_PAY")
@RequiredArgsConstructor
public class AlipayH5PayHandler extends AbstractPayOrderHandler {

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
			bizContent.set("product_code", "QUICK_WAP_WAY");
			AlipayTradeWapPayRequest alipayTradeWapPayRequest = new AlipayTradeWapPayRequest();
			// 异步接收地址，仅支持http/https，公网可访问
			alipayTradeWapPayRequest.setNotifyUrl(notifyUrl);
			// 同步跳转地址，仅支持http/https
			alipayTradeWapPayRequest.setReturnUrl(payTradeOrder.getReturnUrl());
			// 用户付款中途退出返回商户网站的地址
			bizContent.set("quit_url", payTradeOrder.getQuitUrl());
			alipayTradeWapPayRequest.setBizContent(bizContent.toString());
			if (StringUtils.hasText(payTradeOrder.getAlipaySubMchid())) {
				alipayTradeWapPayRequest.putOtherTextParam("app_auth_token", payTradeOrder.getAlipaySubMchid());
			}
			if (payTradeOrder.getProfitSharingStatus().equals(CommonConstants.YES)) {
				JSONObject extendParams = new JSONObject();
				extendParams.set("royalty_freeze", true);
				bizContent.set("extend_params", extendParams.toString());
			}
			AlipayTradeWapPayResponse alipayTradeWapPayResponse = alipayClient.pageExecute(alipayTradeWapPayRequest,
					"get");
			if (!alipayTradeWapPayResponse.isSuccess()) {
				throw new BootBusinessException(
						alipayTradeWapPayResponse.getMsg() + alipayTradeWapPayResponse.getSubMsg());
			}
			return alipayTradeWapPayResponse;
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
		order.setTradeType(PayTradeTypeEnum.ALI_H5_PAY.getName());
		order.setAmount(new BigDecimal(dto.getTotalAmount()));
		order.setNotifyUrl(dto.getNotifyUrl());
		order.setExtra(dto.getExtra());
		order.setTerminalType(PayTerminalTypeEnum.H5.getCode());
		order.setPayMode(PayChannelContext.getChannel().getMode());
		order.setReturnUrl(dto.getReturnUrl());
		order.setQuitUrl(dto.getQuitUrl());
		order.setAlipaySubMchid(dto.getAlipaySubMchid());
		order.setProfitSharingStatus(dto.getProfitSharingStatus());
		order.setUserId(dto.getUserId());
		payTradeOrderService.save(order);
		return order;
	}

	@Override
	public PayConfigVO preParams(CreateOrderReqDTO createOrderReqDTO) {
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_2, PayTerminalTypeEnum.H5.getCode());
		if (null == payConfig) {
			throw new IllegalArgumentException("未配置支付宝支付");
		}
		return payConfig;
	}

}
