package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_rebate_rule_version")
public class RebateRuleVersion {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String ruleId;

	private Integer version;

	private String conditionTree;

	private BigDecimal rate;

	private String supersedes;

	private LocalDateTime createdAt;

}
