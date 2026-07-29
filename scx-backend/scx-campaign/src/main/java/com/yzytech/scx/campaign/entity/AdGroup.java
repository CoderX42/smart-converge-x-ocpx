package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_ad_group")
public class AdGroup {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String campaignId;

	private String name;

	private String status;

	private String optimizationGoalId;

	private String budgetPlanId;

	private String targetingRevisionId;

	private BigDecimal bidCap;

	private Integer version;

	private String currentRevisionId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
