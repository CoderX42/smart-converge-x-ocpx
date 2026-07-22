
package com.yzytech.scx.common.core.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderRefundSuccessEvent {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "订单主键")
	private String orderId;

	@Schema(description = "订单编号")
	private String orderNo;

	@Schema(description = "订单项主键")
	private String orderItemId;

	@Schema(description = "退款状态")
	private String status;

	@Schema(description = "退款到账状态：0.未退款；1.退款中；2.退款成功；3.退款失败；")
	private String arrivalStatus;

	@Schema(description = "支付金额（总金额-优惠券优惠金额+运费 = 支付金额）")
	private BigDecimal paymentPrice;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

	@Schema(description = "退款流水号")
	private String refundTradeNo;

	@Schema(description = "退款原因")
	private String refundReason;

	@Schema(description = "拒绝退款原因")
	private String refuseReason;

	@Schema(description = "退款入账账户")
	private String userReceivedAccount;

	@Schema(description = "退款单号")
	private String refundId;

	@Schema(description = "用户Id")
	private String userId;

	@Schema(description = "店铺ID")
	private String shopId;

	@Schema(description = "退款类型")
	private String refundType;

	@Schema(description = "退款原因ID")
	private String refundReasonId;

	@Schema(description = "冗余退款原因名称")
	private String refundReasonName;

	@Schema(description = "退货时间")
	private LocalDateTime userReturnTime;

	@Schema(description = "退货物流单号")
	private String returnLogisticsNo;

	@Schema(description = "退货物流公司")
	private String returnLogisticsCompany;

	@Schema(description = "退货联系电话")
	private String returnLogisticsPhone;

	@Schema(description = "退货地址")
	private String returnAddress;

	@Schema(description = "用户优惠券id")
	private String couponUserId;

	@Schema(description = "订单类型：1.普通订单；2.多人拼团；3.秒杀")
	private String orderType;

	@Schema(description = "活动ID（如秒杀活动 eventId）")
	private String eventId;

	@Schema(description = "支付类型")
	private String paymentType;

	@Schema(description = "回退抵扣积分")
	private Integer refundDeductPoint;

	@Schema(description = "回退赠送积分")
	private Integer refundEarnedPoint;

	@Schema(description = "服务费比例")
	private BigDecimal serviceFeeRate;

	@Schema(description = "分账状态")
	private String profitSharingStatus;

	private OrderItemRefundSuccessEvent orderItem;

}
