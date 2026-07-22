
package com.yzytech.scx.common.core.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单支付成功内部事件实体
 *
 * @date 2025/4/8
 */

@Data
public class OrderPaySuccessEvent implements Serializable {

	private String orderId;

	private String orderNo;

	private String userId;

	private String shopId;

	private String paymentType;

	private BigDecimal paymentPrice;

	private BigDecimal freightPrice;

	private Long eventTime;

	private Integer earnedPoint;

	private Integer paymentPoint;

	private String couponUserId;

	private BigDecimal totalPrice;

	private String orderType;

	private String eventId;

	private String relationId;

	private LocalDateTime paymentTime;

	private String profitSharingStatus;

	private String transactionId;

	private List<OrderItemPaySuccessEvent> itemList;

}
