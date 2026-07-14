

package com.yzytech.scx.pay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryRequest;
import com.github.binarywang.wxpay.bean.request.WxPayPartnerOrderQueryV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryResult;
import com.github.binarywang.wxpay.bean.result.WxPayPartnerOrderQueryV3Result;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.constant.RocketMqConstants;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.config.AliPayConfiguration;
import com.yzytech.scx.pay.config.WxPayConfiguration;
import com.yzytech.scx.pay.mapper.PayTradeOrderMapper;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 支付订单
 *
 * @date 2022/6/16
 */
@Service
@RequiredArgsConstructor
public class PayTradeOrderServiceImpl extends ServiceImpl<PayTradeOrderMapper, PayTradeOrder>
		implements IPayTradeOrderService {

	private final RocketMQTemplate rocketMQTemplate;

	@Override
	public boolean closePayOrder(String orderNo, String shopId) {
		PayTradeOrder tradeOrder = getOne(
				Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, orderNo));
		if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getExtra())) {
			return true;
		}

		String payType = JSON.parseObject(tradeOrder.getExtra()).getString(PayConstants.EXTRA_PARAMS_PAY_TYPE);

		return switch (payType) {
			case PayConstants.PAY_TYPE_0 -> handleMockPay(tradeOrder);
			case PayConstants.PAY_TYPE_1 -> handleWxPay(tradeOrder, shopId);
			case PayConstants.PAY_TYPE_2 -> handleAliPay(tradeOrder);
			default -> throw new RuntimeException("不支持的支付类型: " + payType);
		};
	}

	private boolean handleMockPay(PayTradeOrder tradeOrder) {
		if (CommonConstants.YES.equals(tradeOrder.getPayStatus())) {
			sendPaySuccessMq(tradeOrder);
			return false;
		}
		updatePayStatusToClosed(tradeOrder);
		return true;
	}

	private boolean handleWxPay(PayTradeOrder tradeOrder, String shopId) {
		try {

			WxPayService wxPayService = WxPayConfiguration.wxPayService(tradeOrder.getTerminalType());
			if (PayConstants.PAY_MODE_SERVICE_PROVIDER.equals(tradeOrder.getPayMode())) {

				WxPayPartnerOrderQueryV3Request wxPayPartnerOrderQueryV3Request = new WxPayPartnerOrderQueryV3Request();
				wxPayPartnerOrderQueryV3Request.setSubMchId(tradeOrder.getWxSubMchid());
				wxPayPartnerOrderQueryV3Request.setOutTradeNo(tradeOrder.getOutTradeNo());
				// 服务商查询订单
				WxPayPartnerOrderQueryV3Result wxPayPartnerOrderQueryV3Result = wxPayService
					.queryPartnerOrderV3(wxPayPartnerOrderQueryV3Request);
				String state = wxPayPartnerOrderQueryV3Result.getTradeState();
				if (WxPayConstants.WxpayTradeStatus.NOTPAY.equals(state)
						|| WxPayConstants.WxpayTradeStatus.CLOSED.equals(state)) {
					updatePayStatusToClosed(tradeOrder);
					return true;
				}
				else {
					updateTradeOrderAsPaid(tradeOrder, wxPayPartnerOrderQueryV3Result.getTransactionId(),
							LocalDateTime.now());
					sendPaySuccessMq(tradeOrder);
					return false;
				}

			}
			else {
				WxPayOrderQueryRequest request = new WxPayOrderQueryRequest();
				request.setOutTradeNo(tradeOrder.getOutTradeNo());
				request.setMchId(wxPayService.getConfig().getMchId());

				WxPayOrderQueryResult result = wxPayService.queryOrder(request);
				String state = result.getTradeState();
				if (WxPayConstants.WxpayTradeStatus.NOTPAY.equals(state)
						|| WxPayConstants.WxpayTradeStatus.CLOSED.equals(state)) {
					updatePayStatusToClosed(tradeOrder);
					return true;
				}
				else {
					updateTradeOrderAsPaid(tradeOrder, result.getTransactionId(), LocalDateTime.now());
					sendPaySuccessMq(tradeOrder);
					return false;
				}
			}

		}
		catch (WxPayException e) {
			log.error("微信支付查询失败", e);
			return true;
		}
	}

	private boolean handleAliPay(PayTradeOrder tradeOrder) {
		try {
			AlipayClient client = AliPayConfiguration.getAlipayClient(tradeOrder.getTerminalType());
			AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();

			AlipayTradeQueryModel alipayTradeQueryModel = new AlipayTradeQueryModel();
			alipayTradeQueryModel.setOutTradeNo(tradeOrder.getOutTradeNo());
			if (StringUtils.hasText(tradeOrder.getAlipaySubMchid())) {
				req.putOtherTextParam("app_auth_token", tradeOrder.getAlipaySubMchid());
			}
			req.setBizModel(alipayTradeQueryModel);
			AlipayTradeQueryResponse resp = client.certificateExecute(req);
			String status = resp.getTradeStatus();

			if (!"TRADE_SUCCESS".equals(status)) {
				updatePayStatusToClosed(tradeOrder);
				return true;
			}
			else {
				LocalDateTime payTime = resp.getSendPayDate()
					.toInstant()
					.atZone(ZoneId.systemDefault())
					.toLocalDateTime();
				updateTradeOrderAsPaid(tradeOrder, resp.getTradeNo(), payTime);
				sendPaySuccessMq(tradeOrder);
				return false;
			}
		}
		catch (Exception e) {
			log.error("支付宝支付查询失败", e);
			return true;
		}
	}

	private void updateTradeOrderAsPaid(PayTradeOrder order, String channelOrderNo, LocalDateTime successTime) {
		if (CommonConstants.NO.equals(order.getPayStatus())) {
			order.setPayStatus(CommonConstants.YES);
			order.setChannelOrderNo(channelOrderNo);
			order.setPaySuccessTime(successTime);
			this.updateById(order);
		}
	}

	private void updatePayStatusToClosed(PayTradeOrder order) {
		order.setPayStatus(PayConstants.PAY_CLOSE_STATUS);
		this.updateById(order);
	}

	private void sendPaySuccessMq(PayTradeOrder order) {
		JSONObject notify = new JSONObject();
		notify.put(PayConstants.OUT_TRADE_NO, order.getOutTradeNo());
		notify.put(PayConstants.CHANNEL_ORDER_NO, order.getChannelOrderNo());
		notify.put(PayConstants.PAY_SUCCESS_TIME, order.getPaySuccessTime());
		notify.put(PayConstants.EXTRA_PARAMS, order.getExtra());
		notify.put("source", null);
		notify.put(PayConstants.TRADE_TYPE, order.getTradeType());

		JSONObject extra = JSON.parseObject(order.getExtra());
		String mqTopic = extra.getString("mqNotifyUrl");

		rocketMQTemplate.syncSend(mqTopic, new GenericMessage<>(notify), RocketMqConstants.TIME_OUT);
	}

}
