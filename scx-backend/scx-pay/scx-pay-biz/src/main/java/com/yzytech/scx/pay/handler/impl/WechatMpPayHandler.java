

package com.yzytech.scx.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayPartnerUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.entity.CallbackPrefixProperties;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.dto.CreateOrderReqDTO;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.enums.PaySceneEnum;
import com.yzytech.scx.pay.api.enums.PayTerminalTypeEnum;
import com.yzytech.scx.pay.api.enums.PayTradeTypeEnum;
import com.yzytech.scx.pay.api.utils.PayChannelContext;
import com.yzytech.scx.pay.api.utils.WxPayHelper;
import com.yzytech.scx.pay.api.vo.PayConfigVO;
import com.yzytech.scx.pay.config.WxPayConfiguration;
import com.yzytech.scx.pay.handler.AbstractPayOrderHandler;
import com.yzytech.scx.pay.service.IPayConfigService;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static com.yzytech.scx.pay.api.constants.PayConstants.ORDER_CREATE_TYPE_PLATFORM;

@Service("WX_MP_PAY")
@RequiredArgsConstructor
public class WechatMpPayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final IPayConfigService payConfigService;

	@Override
	public Object doPay(PayTradeOrder payTradeOrder) {
		return switch (resolvePayScene(payTradeOrder)) {
			case DIRECT -> doDirectPay(payTradeOrder);
			case PARTNER_SINGLE -> doPartnerPay(payTradeOrder);
			case PARTNER_COMBINE -> null;
		};
	}

	private PaySceneEnum resolvePayScene(PayTradeOrder order) {
		if (PayConstants.PAY_MODE_SERVICE_PROVIDER.equals(order.getPayMode())) {
			return PaySceneEnum.PARTNER_SINGLE;
		}
		return PaySceneEnum.DIRECT;
	}

	/** 普通商户支付 */
	private Object doDirectPay(PayTradeOrder order) {
		WxPayService wxPayService = WxPayConfiguration.wxPayService(PayChannelContext.getChannel());
		WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
		request.setOutTradeNo(order.getOutTradeNo());
		request.setDescription(order.getDescription());
		request.setNotifyUrl(WxPayHelper.buildNotifyUrl(order.getNotifyUrl(), callbackPrefixProperties.getPay(),
				"notify", order.getTerminalType()));
		request.setAmount(buildAmount(order.getAmount()));
		WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
		payer.setOpenid(order.getOpenId());
		request.setPayer(payer);
		try {
			return wxPayService.createOrderV3(TradeTypeEnum.JSAPI, request);
		}
		catch (WxPayException e) {
			throw new BootBusinessException(e.getMessage());
		}
	}

	private WxPayUnifiedOrderV3Request.Amount buildAmount(BigDecimal amount) {
		WxPayUnifiedOrderV3Request.Amount a = new WxPayUnifiedOrderV3Request.Amount();
		a.setCurrency(PayConstants.CURRENCY);
		a.setTotal(WxPayHelper.amountToFen(amount));
		return a;
	}

	/**
	 * 服务商单笔支付
	 */
	public Object doPartnerPay(PayTradeOrder order) {
		WxPayService wxPayService = WxPayConfiguration.wxPayService(PayChannelContext.getChannel());
		WxPayPartnerUnifiedOrderV3Request request = new WxPayPartnerUnifiedOrderV3Request();
		request.setOutTradeNo(order.getOutTradeNo());
		request.setDescription(order.getDescription());
		request.setNotifyUrl(WxPayHelper.buildNotifyUrl(order.getNotifyUrl(), callbackPrefixProperties.getPay(),
				"notify/partner-notify", order.getTerminalType()));
		request.setSubMchId(order.getWxSubMchid());
		if (StringUtils.hasText(order.getWxSubAppid())) {
			request.setSubAppid(order.getWxSubAppid());
		}
		request.setAmount(buildPartnerAmount(order.getAmount()));
		WxPayPartnerUnifiedOrderV3Request.Payer payer = new WxPayPartnerUnifiedOrderV3Request.Payer();
		payer.setSpOpenid(order.getOpenId());
		request.setPayer(payer);
		if (CommonConstants.YES.equals(order.getProfitSharingStatus())) {
			WxPayPartnerUnifiedOrderV3Request.SettleInfo settleInfo = new WxPayPartnerUnifiedOrderV3Request.SettleInfo();
			settleInfo.setProfitSharing(true);
			request.setSettleInfo(settleInfo);
		}
		try {
			return wxPayService.createPartnerOrderV3(TradeTypeEnum.JSAPI, request);
		}
		catch (WxPayException e) {
			throw new BootBusinessException(e.getMessage());
		}
	}

	private WxPayPartnerUnifiedOrderV3Request.Amount buildPartnerAmount(BigDecimal amount) {
		WxPayPartnerUnifiedOrderV3Request.Amount a = new WxPayPartnerUnifiedOrderV3Request.Amount();
		a.setCurrency(PayConstants.CURRENCY);
		a.setTotal(WxPayHelper.amountToFen(amount));
		return a;
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO dto) {
		return Optional.ofNullable(findExistOrder(dto)).orElseGet(() -> buildNewOrder(dto));
	}

	@Override
	public PayConfigVO preParams(CreateOrderReqDTO createOrderReqDTO) {
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_1,
				PayTerminalTypeEnum.OFFICIAL_ACCOUNT.getCode());
		if (null == payConfig) {
			throw new IllegalArgumentException("未配置微信支付");
		}
		return payConfig;
	}

	private PayTradeOrder findExistOrder(CreateOrderReqDTO dto) {
		PayTradeOrder payTradeOrder = payTradeOrderService
			.getOne(Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, dto.getOutTradeNo()));
		if (Objects.nonNull(payTradeOrder)) {
			BeanUtils.copyProperties(dto, payTradeOrder);
			payTradeOrderService.updateById(payTradeOrder);
		}
		return payTradeOrder;
	}

	private PayTradeOrder buildNewOrder(CreateOrderReqDTO dto) {
		PayTradeOrder order = new PayTradeOrder();
		order.setUserId(dto.getUserId());
		order.setPayStatus(CommonConstants.NO);
		order.setDescription(dto.getSubject());
		order.setOpenId(dto.getBuyerId());
		order.setOutTradeNo(dto.getOutTradeNo());
		order.setTradeType(PayTradeTypeEnum.WX_JSAPI_PAY.getName());
		order.setAmount(new BigDecimal(dto.getTotalAmount()));
		order.setNotifyUrl(dto.getNotifyUrl());
		order.setExtra(dto.getExtra());
		order.setTerminalType(PayTerminalTypeEnum.OFFICIAL_ACCOUNT.getCode());
		order.setPayMode(PayChannelContext.getChannel().getMode());
		order.setWxSubAppid(dto.getSubAppId());
		if (ORDER_CREATE_TYPE_PLATFORM.equals(dto.getOrderType())) {
			order.setWxSubMchid(PayChannelContext.getChannel().getSubMchid());
		}
		else {
			order.setWxSubMchid(dto.getWxSubMchid());
		}
		order.setProfitSharingStatus(dto.getProfitSharingStatus());
		payTradeOrderService.save(order);
		return order;
	}

}
