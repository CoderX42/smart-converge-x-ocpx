

package com.yzytech.scx.common.core.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单项支付成功内部事件实体
 *
 * @date 2025/4/8
 */
@Data
public class OrderItemRefundSuccessEvent {

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "skuId")
	private String skuId;

	@Schema(description = "购买数量")
	private Integer buyQuantity;

}
