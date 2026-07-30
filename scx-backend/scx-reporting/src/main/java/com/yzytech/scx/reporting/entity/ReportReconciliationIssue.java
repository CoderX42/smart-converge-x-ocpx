package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_report_reconciliation_issue")
public class ReportReconciliationIssue {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaId;

	private LocalDate reportDate;

	private String issueType;

	private String resourceId;

	private String detailJson;

	private String status;

	private LocalDateTime detectedAt;

	private LocalDateTime resolvedAt;

}
