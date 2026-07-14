

package com.yzytech.scx.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商户订单号")
	private String outTradeNo;

	@Schema(description = "订单总金额 单位为元，精确到小数点后两位，取值范围为 [0.01,100000000]。金额不能为0。")
	private String totalAmount;

	@Schema(description = "订单标题。注意：不可使用特殊字符，如 /，=，& 等。")
	private String subject;

	@Schema(description = "通知地址")
	private String notifyUrl;

	@Schema(description = "支付类型")
	private String tradeType;

	@Schema(description = "同步跳转地址，仅支持http/https")
	private String returnUrl;

	@Schema(description = "用户付款中途退出返回商户网站的地址")
	private String quitUrl;

	@Schema(description = "买家用户ID")
	private String buyerId;

	@Schema(description = "额外参数")
	private String extra;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "类型：0.店铺订单；1.平台业务")
	private String orderType;

	@Schema(description = "服务费率(下单时快照)")
	private BigDecimal serviceRate;

	@Schema(description = "子商户号")
	private String wxSubMchid;

	@Schema(description = "子商户应用AppId")
	private String subAppId;

	@Schema(description = "订单数量")
	private int orderNum;

	@Schema(description = "支付宝子商户号")
	private String alipaySubMchid;

	@Schema(description = "分账状态：0=否，1=是")
	private String profitSharingStatus;

	@Schema(description = "应用ID")
	private String appId;

}
