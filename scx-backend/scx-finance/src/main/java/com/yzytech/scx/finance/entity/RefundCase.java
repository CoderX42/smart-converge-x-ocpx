package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_refund_case")
public class RefundCase {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private BigDecimal amount;

	private String refundReason;

	private String status;

	private String makerId;

	private String checkerId;

	private LocalDateTime submittedAt;

	private LocalDateTime reviewedAt;

	private LocalDateTime completedAt;

	private String journalId;

	private String reversalJournalId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
