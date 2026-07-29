package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_closing_period")
public class ClosingPeriod {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private LocalDate periodStart;

	private LocalDate periodEnd;

	private String timezone;

	private String status;

	private LocalDateTime closedAt;

	private String closedBy;

	private String inputHash;

	private BigDecimal diffThreshold;

	private String preCloseSnapshotId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	private LocalDateTime createTime;

}
