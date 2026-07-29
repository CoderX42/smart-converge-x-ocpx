package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_credit_line")
public class CreditLine {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private BigDecimal creditAmount;

	private BigDecimal usedAmount;

	private BigDecimal availableAmount;

	private String currency;

	private String status;

	private LocalDateTime grantedAt;

	private LocalDateTime expiredAt;

	@Version
	private Integer version;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
