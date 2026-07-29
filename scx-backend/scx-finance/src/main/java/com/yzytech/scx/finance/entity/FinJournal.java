package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_fin_journal")
public class FinJournal {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String journalType;

	private String businessType;

	private String businessId;

	private String orgId;

	private String currency;

	private BigDecimal totalDebit;

	private BigDecimal totalCredit;

	private String status;

	private String memo;

	private LocalDateTime postedAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	private LocalDateTime createTime;

}
