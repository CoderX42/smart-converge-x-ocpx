package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("scx_optimization_goal")
public class OptimizationGoal {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String eventCode;

	private BigDecimal targetCpa;

	private String targetCpaCurrency;

	private Integer attributionWindowHours;

	private String status;

}
