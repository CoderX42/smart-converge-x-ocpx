package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_rebate_calculation")
public class RebateCalculation {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String ruleId;

	private Integer ruleVersion;

	private BigDecimal baseAmount;

	private BigDecimal rebateAmount;

	private LocalDate periodStart;

	private LocalDate periodEnd;

	private String status;

	private String revisionOf;

	private BigDecimal difference;

	private String journalId;

	private LocalDateTime createdAt;

}
