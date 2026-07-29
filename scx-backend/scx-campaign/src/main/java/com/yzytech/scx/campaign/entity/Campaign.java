package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_campaign")
public class Campaign {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String adAccountId;

	private String name;

	private String objective;

	private String billingMode;

	private BigDecimal targetCpa;

	private BigDecimal totalBudget;

	private BigDecimal dailyBudget;

	private String currency;

	private String timezone;

	private LocalDateTime startAt;

	private LocalDateTime endAt;

	private String status;

	private Integer version;

	private String currentRevisionId;

	private String budgetReservationId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
