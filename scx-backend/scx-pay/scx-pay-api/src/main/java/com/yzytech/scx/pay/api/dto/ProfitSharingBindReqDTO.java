

package com.yzytech.scx.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ProfitSharingBindReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "接收方类型")
	private String type;

	@Schema(description = "接收方账号")
	private String account;

	@Schema(description = "分账接收方全称")
	private String name;

	@Schema(description = "与分账方的关系类型")
	private String relationType;

	@Schema(description = "自定义的分账关系")
	private String customRelation;

	@Schema(description = "外部请求号")
	private String outRequestNo;

	@Schema(description = "渠道类型：1=微信；2=支付宝")
	private String channelType;

	@Schema(description = "子商户号")
	private String subMchid;

}
