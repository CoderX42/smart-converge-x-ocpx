package com.yzytech.scx.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("scx_invoice_item")
public class InvoiceItem {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String applicationId;

	private String itemDesc;

	private BigDecimal amount;

	private LocalDate periodStart;

	private LocalDate periodEnd;

}
