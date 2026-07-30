package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_report_backfill")
public class ReportBackfill {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaId;

	private LocalDate startDate;

	private LocalDate endDate;

	private Boolean dryRun;

	private String status;

	private Integer processedDates;

	private Long oldRowCount;

	private Long newRowCount;

	private String differenceJson;

	private String requestedBy;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	private String errorMessage;

	private LocalDateTime createTime;

}
