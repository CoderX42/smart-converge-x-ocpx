package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("scx_reconciliation_item")
public class ReconciliationItem {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String runId;

	private String itemType;

	private String referenceId;

	private BigDecimal scxAmount;

	private BigDecimal externalAmount;

	private BigDecimal difference;

	private String note;

}
