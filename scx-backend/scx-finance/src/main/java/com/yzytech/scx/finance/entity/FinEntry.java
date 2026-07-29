package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_fin_entry")
public class FinEntry {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String journalId;

	private String finAccountId;

	private String direction;

	private BigDecimal amount;

	private BigDecimal balanceAfter;

	private String memo;

	private LocalDateTime createTime;

}
