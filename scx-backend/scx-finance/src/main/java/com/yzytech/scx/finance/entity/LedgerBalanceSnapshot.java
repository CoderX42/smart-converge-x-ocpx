package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_ledger_balance_snapshot")
public class LedgerBalanceSnapshot {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String snapshotType;

	private LocalDate snapshotDate;

	private BigDecimal cashBalance;

	private BigDecimal creditBalance;

	private BigDecimal reservedTotal;

	private BigDecimal estimatedSpend;

	private BigDecimal confirmedSpend;

	private BigDecimal rebateBalance;

	private String currency;

	private String inputHash;

	private LocalDateTime snapshotAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	private LocalDateTime createTime;

}
