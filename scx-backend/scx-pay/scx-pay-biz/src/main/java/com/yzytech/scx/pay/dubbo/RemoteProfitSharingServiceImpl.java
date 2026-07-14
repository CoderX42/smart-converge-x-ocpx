

package com.yzytech.scx.pay.dubbo;

import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.request.AlipayTradeOrderSettleRequest;
import com.alipay.api.request.AlipayTradeRoyaltyRelationBindRequest;
import com.alipay.api.request.AlipayTradeRoyaltyRelationUnbindRequest;
import com.alipay.api.response.AlipayTradeOrderSettleResponse;
import com.alipay.api.response.AlipayTradeRoyaltyRelationBindResponse;
import com.alipay.api.response.AlipayTradeRoyaltyRelationUnbindResponse;
import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingReceiverV3Request;
import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingV3Request;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.util.SnowflakeIdUtils;
import com.yzytech.scx.pay.api.dto.ProfitSharingBindReqDTO;
import com.yzytech.scx.pay.api.dto.ProfitSharingSettleReqDTO;
import com.yzytech.scx.pay.api.dto.ProfitSharingUnbindReqDTO;
import com.yzytech.scx.pay.api.enums.PayTerminalTypeEnum;
import com.yzytech.scx.pay.api.remote.RemoteProfitSharingService;
import com.yzytech.scx.pay.api.utils.WxPayHelper;
import com.yzytech.scx.pay.config.AliPayConfiguration;
import com.yzytech.scx.pay.config.WxPayConfiguration;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.yzytech.scx.pay.api.constants.PayConstants.PROFIT_SHARING_CHANNEL_TYPE_1;
import static com.yzytech.scx.pay.api.constants.PayConstants.PROFIT_SHARING_CHANNEL_TYPE_2;

/**
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteProfitSharingServiceImpl implements RemoteProfitSharingService {

	@Override
	public boolean bind(ProfitSharingBindReqDTO profitSharingBindReqDTO) {
		switch (profitSharingBindReqDTO.getChannelType()) {
			case PROFIT_SHARING_CHANNEL_TYPE_1:
				WxPayService wxPayService = WxPayConfiguration.wxPayService(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
				ProfitSharingReceiverV3Request request = new ProfitSharingReceiverV3Request();
				request.setAccount(profitSharingBindReqDTO.getAccount());
				request.setName(profitSharingBindReqDTO.getName());
				request.setType(profitSharingBindReqDTO.getType());
				request.setRelationType(profitSharingBindReqDTO.getRelationType());
				if (StringUtils.hasText(profitSharingBindReqDTO.getCustomRelation())) {
					request.setCustomRelation(profitSharingBindReqDTO.getCustomRelation());
				}
				request.setAppid(wxPayService.getConfig().getAppId());
				request.setSubMchId(profitSharingBindReqDTO.getSubMchid());
				try {
					wxPayService.getProfitSharingService().addReceiverV3(request);
				}
				catch (WxPayException e) {
					throw new RuntimeException(e.getLocalizedMessage());
				}
				break;
			case PROFIT_SHARING_CHANNEL_TYPE_2:
				try {
					AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(PayTerminalTypeEnum.H5.getCode());
					AlipayTradeRoyaltyRelationBindRequest alipayTradeRoyaltyRelationBindRequest = new AlipayTradeRoyaltyRelationBindRequest();
					AlipayTradeRoyaltyRelationBindModel model = new AlipayTradeRoyaltyRelationBindModel();
					// 设置分账接收方列表
					List<RoyaltyEntity> receiverList = new ArrayList<RoyaltyEntity>();
					RoyaltyEntity royalty = new RoyaltyEntity();
					royalty.setMemo(profitSharingBindReqDTO.getCustomRelation());
					royalty.setType(profitSharingBindReqDTO.getType());
					royalty.setAccount(profitSharingBindReqDTO.getAccount());
					royalty.setName(profitSharingBindReqDTO.getName());
					receiverList.add(royalty);
					model.setReceiverList(receiverList);
					// 设置外部请求号
					model.setOutRequestNo(profitSharingBindReqDTO.getOutRequestNo());

					alipayTradeRoyaltyRelationBindRequest.setBizModel(model);
					// 第三方代调用模式下请设置app_auth_token
					alipayTradeRoyaltyRelationBindRequest.putOtherTextParam("app_auth_token",
							profitSharingBindReqDTO.getSubMchid());
					AlipayTradeRoyaltyRelationBindResponse alipayTradeRoyaltyRelationBindResponse = alipayClient
						.certificateExecute(alipayTradeRoyaltyRelationBindRequest);
					if (!alipayTradeRoyaltyRelationBindResponse.isSuccess()) {
						throw new RuntimeException(alipayTradeRoyaltyRelationBindResponse.getSubMsg());
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				break;
			default:
				throw new IllegalArgumentException("类型错误");
		}
		return true;
	}

	@Override
	public boolean unbind(ProfitSharingUnbindReqDTO profitSharingUnBindReqDTO) {
		switch (profitSharingUnBindReqDTO.getChannelType()) {
			case PROFIT_SHARING_CHANNEL_TYPE_1:
				WxPayService wxPayService = WxPayConfiguration.wxPayService(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
				ProfitSharingReceiverV3Request request = new ProfitSharingReceiverV3Request();
				request.setAccount(profitSharingUnBindReqDTO.getAccount());
				request.setType(profitSharingUnBindReqDTO.getType());
				request.setAppid(wxPayService.getConfig().getAppId());
				request.setSubMchId(profitSharingUnBindReqDTO.getSubMchid());
				try {
					wxPayService.getProfitSharingService().removeReceiverV3(request);
				}
				catch (WxPayException e) {
					throw new RuntimeException(e.getLocalizedMessage());
				}
				break;
			case PROFIT_SHARING_CHANNEL_TYPE_2:
				try {
					AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(PayTerminalTypeEnum.H5.getCode());
					AlipayTradeRoyaltyRelationUnbindRequest alipayTradeRoyaltyRelationUnbindRequest = new AlipayTradeRoyaltyRelationUnbindRequest();
					AlipayTradeRoyaltyRelationUnbindModel model = new AlipayTradeRoyaltyRelationUnbindModel();
					// 设置分账接收方列表
					List<RoyaltyEntity> receiverList = new ArrayList<RoyaltyEntity>();
					RoyaltyEntity royalty = new RoyaltyEntity();
					royalty.setType(profitSharingUnBindReqDTO.getType());
					royalty.setAccount(profitSharingUnBindReqDTO.getAccount());
					receiverList.add(royalty);
					model.setReceiverList(receiverList);
					// 设置外部请求号
					model.setOutRequestNo(profitSharingUnBindReqDTO.getOutRequestNo());

					alipayTradeRoyaltyRelationUnbindRequest.setBizModel(model);
					// 第三方代调用模式下请设置app_auth_token
					alipayTradeRoyaltyRelationUnbindRequest.putOtherTextParam("app_auth_token",
							profitSharingUnBindReqDTO.getSubMchid());
					AlipayTradeRoyaltyRelationUnbindResponse alipayTradeRoyaltyRelationUnbindResponse = alipayClient
						.certificateExecute(alipayTradeRoyaltyRelationUnbindRequest);
					if (!alipayTradeRoyaltyRelationUnbindResponse.isSuccess()) {
						throw new RuntimeException(alipayTradeRoyaltyRelationUnbindResponse.getMsg());
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				break;
			default:
				throw new IllegalArgumentException("类型错误");
		}
		return true;
	}

	@Override
	public boolean profitSharing(ProfitSharingSettleReqDTO profitSharingSettleReqDTO) {
		if (profitSharingSettleReqDTO.getChannelType().equals(CommonConstants.YES)) {
			WxPayService wxPayService = WxPayConfiguration.wxPayService(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
			try {
				// 调用分账
				ProfitSharingV3Request request = new ProfitSharingV3Request();
				request.setSubMchId(profitSharingSettleReqDTO.getSubMchid());
				request.setOutOrderNo(SnowflakeIdUtils.orderNo());
				request.setTransactionId(profitSharingSettleReqDTO.getTransactionId());
				if (Objects.nonNull(profitSharingSettleReqDTO.getUnfreezeUnsplit())) {
					request.setUnfreezeUnsplit(profitSharingSettleReqDTO.getUnfreezeUnsplit());
				}
				ProfitSharingV3Request.Receiver receiver = new ProfitSharingV3Request.Receiver();
				receiver.setDescription("分账: " + profitSharingSettleReqDTO.getAmount());
				receiver.setType(profitSharingSettleReqDTO.getType());
				receiver.setAmount(WxPayHelper.amountToFen(profitSharingSettleReqDTO.getAmount()));
				receiver.setAccount(profitSharingSettleReqDTO.getAccount());
				request.setReceivers(List.of(receiver));
				request.setAppid(wxPayService.getConfig().getAppId());
				wxPayService.getProfitSharingService().profitSharingV3(request);
			}
			catch (WxPayException e) {
				throw new RuntimeException(e.getLocalizedMessage());
			}
		}
		else {
			try {
				AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(PayTerminalTypeEnum.H5.getCode());

				List<OpenApiRoyaltyDetailInfoPojo> royaltyParameters = new ArrayList<>();
				OpenApiRoyaltyDetailInfoPojo pojo = new OpenApiRoyaltyDetailInfoPojo();
				pojo.setAmount(String.valueOf(profitSharingSettleReqDTO.getAmount()));
				pojo.setTransInType(profitSharingSettleReqDTO.getType());
				pojo.setDesc("分账: " + profitSharingSettleReqDTO.getAmount());
				pojo.setTransIn(profitSharingSettleReqDTO.getAccount());
				royaltyParameters.add(pojo);

				AlipayTradeOrderSettleModel alipayTradeOrderSettleModel = new AlipayTradeOrderSettleModel();
				alipayTradeOrderSettleModel.setOutRequestNo(profitSharingSettleReqDTO.getOrderNo());
				alipayTradeOrderSettleModel.setTradeNo(profitSharingSettleReqDTO.getTransactionId());
				alipayTradeOrderSettleModel.setRoyaltyParameters(royaltyParameters);
				// 第三方代调用模式下请设置app_auth_token
				AlipayTradeOrderSettleRequest alipayTradeOrderSettleQueryRequest = new AlipayTradeOrderSettleRequest();
				alipayTradeOrderSettleQueryRequest.setBizModel(alipayTradeOrderSettleModel);
				alipayTradeOrderSettleQueryRequest.putOtherTextParam("app_auth_token",
						profitSharingSettleReqDTO.getSubMchid());
				AlipayTradeOrderSettleResponse alipayTradeOrderSettleResponse = alipayClient
					.certificateExecute(alipayTradeOrderSettleQueryRequest);
				if (!alipayTradeOrderSettleResponse.isSuccess()) {
					throw new RuntimeException(alipayTradeOrderSettleResponse.getMsg());
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		return true;
	}

}
