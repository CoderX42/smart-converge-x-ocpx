package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_adjustment_journal")
public class AdjustmentJournal {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String closingPeriodId;

	private String journalId;

	private String adjustmentType;

	private BigDecimal amount;

	private String reason;

	private String approvedBy;

	private LocalDateTime createdAt;

}
