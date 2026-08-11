package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_deduction_rule")
public class OcpxDeductionRule {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelId;

	private String appLinkId;

	private BigDecimal ratio;

	private Long configVersion;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
