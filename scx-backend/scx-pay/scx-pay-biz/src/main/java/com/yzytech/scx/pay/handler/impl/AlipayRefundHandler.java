

package com.yzytech.scx.pay.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.RocketMqConstants;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.dto.CreateRefundsReqDTO;
import com.yzytech.scx.pay.api.entity.PayRefundOrder;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.api.enums.PayRefundOrderStatusEnum;
import com.yzytech.scx.pay.api.utils.TransactionalMqUtils;
import com.yzytech.scx.pay.config.AliPayConfiguration;
import com.yzytech.scx.pay.handler.AbstractPayRefundOrderHandler;
import com.yzytech.scx.pay.service.IPayRefundOrderService;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("ALIPAY_REFUND")
@RequiredArgsConstructor
public class AlipayRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final RocketMQTemplate rocketMQTemplate;

	private final IPayTradeOrderService tradeOrderService;

	@Override
	public Object doRefund(PayRefundOrder payRefundOrder) {
		// 查询支付单
		PayTradeOrder payTradeOrder = tradeOrderService.getOne(
				Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, payRefundOrder.getOutTradeNo()));

		AlipayClient alipayClient = null;
		try {
			alipayClient = AliPayConfiguration.getAlipayClient(payTradeOrder.getTerminalType());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
		AlipayTradeRefundModel model = new AlipayTradeRefundModel();
		model.setRefundAmount(String.valueOf(payRefundOrder.getRefundAmount()));
		model.setOutTradeNo(payRefundOrder.getOutTradeNo());
		model.setOutRequestNo(payRefundOrder.getRefundTradeNo());
		request.setBizModel(model);

		if (StringUtils.hasText(payTradeOrder.getAlipaySubMchid())) {
			request.putOtherTextParam("app_auth_token", payTradeOrder.getAlipaySubMchid());
		}
		AlipayTradeRefundResponse response = null;
		try {
			response = alipayClient.certificateExecute(request);
		}
		catch (AlipayApiException e) {
			throw new BootBusinessException(e.getErrMsg());
		}
		if (!response.isSuccess()) {
			throw new BootBusinessException(response.getMsg() + response.getSubMsg());
		}
		if ("Y".equals(response.getFundChange())) {
			// 退款成功发送mq消息
			TransactionalMqUtils.sendAfterCommit(() -> {
				// 🚀 在事务成功提交之后再发 MQ
				JSONObject jsonObject = new JSONObject();
				jsonObject.put(PayConstants.EXTRA_PARAMS, payRefundOrder.getExtra());
				jsonObject.put(PayConstants.REFUND_TRADE_NO, payRefundOrder.getRefundTradeNo());
				jsonObject.put("source", null);
				rocketMQTemplate.syncSend(RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC, new GenericMessage<>(jsonObject),
						RocketMqConstants.TIME_OUT);
			});
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
		}
		else {
			// 退款接口返回 fund_change=N
			// 不代表交易没有退款，只是代表该次接口请求没有资金变动，此时需使用退款查询接口进行查询判断，该笔交易是退款失败，还是退款成功后重复操作导致。
			// 状态更改成待确认 定时任务跑批处理
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_4.getCode());
		}
		payRefundOrderService.updateById(payRefundOrder);

		return response;
	}

	@Override
	public PayRefundOrder createRefundOrder(CreateRefundsReqDTO createRefundsReqDTO) {
		PayRefundOrder payRefundOrder = payRefundOrderService.getOne(Wrappers.<PayRefundOrder>lambdaQuery()
			.eq(PayRefundOrder::getRefundTradeNo, createRefundsReqDTO.getRefundTradeNo()));
		if (null != payRefundOrder) {
			return payRefundOrder;
		}
		payRefundOrder = new PayRefundOrder();
		payRefundOrder.setRefundAmount(createRefundsReqDTO.getRefundAmount());
		payRefundOrder.setPayAmount(createRefundsReqDTO.getTotalAmount());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_1.getCode());
		payRefundOrder.setRefundTradeNo(createRefundsReqDTO.getRefundTradeNo());
		payRefundOrder.setNotifyUrl(createRefundsReqDTO.getNotifyUrl());
		payRefundOrder.setOutTradeNo(createRefundsReqDTO.getOutTradeNo());
		payRefundOrder.setExtra(createRefundsReqDTO.getExtra());
		payRefundOrder.setUserId(createRefundsReqDTO.getUserId());
		payRefundOrderService.save(payRefundOrder);
		return payRefundOrder;
	}

}
