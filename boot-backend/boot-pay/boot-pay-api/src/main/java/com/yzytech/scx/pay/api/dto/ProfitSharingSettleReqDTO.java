
package com.yzytech.scx.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProfitSharingSettleReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "分账金额")
	private BigDecimal amount;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "子商户号")
	private String subMchid;

	@Schema(description = "店铺ID")
	private String shopId;

	@Schema(description = "渠道类型：1=微信；2=支付宝")
	private String channelType;

	@Schema(description = "接收方类型")
	private String type;

	@Schema(description = "接收方账号")
	private String account;

	@Schema(description = "分账接收方全称")
	private String name;

	@Schema(description = "分账单号")
	private String outOrderNo;

	@Schema(description = "微信支付单号")
	private String transactionId;

	private Boolean unfreezeUnsplit;

}
