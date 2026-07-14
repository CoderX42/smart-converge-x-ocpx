

package com.yzytech.scx.common.core.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单完成内部事件实体
 *
 * @date 2025/4/8
 */
@Data
public class OrderCompleteEvent {

	@Schema(description = "订单主键")
	private String orderId;

	@Schema(description = "订单编号")
	private String orderNo;

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "店铺ID")
	private String shopId;@Schema(description = "赠送积分数量")
	private Integer earnedPoint;

	@Schema(description = "用户ID")
	private String userId;

}
