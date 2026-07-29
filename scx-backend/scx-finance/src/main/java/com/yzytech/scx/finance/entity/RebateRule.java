package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_rebate_rule")
public class RebateRule {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String ruleName;

	private String ruleType;

	private String conditionTree;

	private BigDecimal rate;

	private String status;

	private LocalDate effectiveFrom;

	private LocalDate effectiveTo;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
