package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_budget_reservation")
public class BudgetReservation {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String adAccountId;

	private String campaignId;

	private BigDecimal reservedAmount;

	private BigDecimal consumedAmount;

	private BigDecimal releasedAmount;

	private BigDecimal cashReservedAmount;

	private BigDecimal creditReservedAmount;

	private BigDecimal cashConsumedAmount;

	private BigDecimal creditConsumedAmount;

	private BigDecimal cashReleasedAmount;

	private BigDecimal creditReleasedAmount;

	private String reserveJournalId;

	private String businessId;

	private String currency;

	private String status;

	private LocalDateTime reservedAt;

	private LocalDateTime releasedAt;

	@Version
	private Integer version;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
