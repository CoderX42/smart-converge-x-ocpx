package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_reconciliation_run")
public class ReconciliationRun {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private LocalDate periodStart;

	private LocalDate periodEnd;

	private String timezone;

	private String inputHash;

	private BigDecimal scxBalance;

	private BigDecimal externalBalance;

	private BigDecimal difference;

	private String status;

	private LocalDateTime resolvedAt;

	private LocalDateTime createdAt;

}
