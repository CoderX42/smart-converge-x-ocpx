package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_invoice_application")
public class InvoiceApplication {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private BigDecimal invoiceAmount;

	private BigDecimal settledAmount;

	private String invoiceStatus;

	private String invoiceNo;

	private LocalDateTime issuedAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
