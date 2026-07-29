package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("scx_budget_plan")
public class BudgetPlan {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private BigDecimal totalAmount;

	private BigDecimal dailyAmount;

	private String pacingStrategy;

	private String currency;

	private String status;

}
