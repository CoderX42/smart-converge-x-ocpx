package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_report_run")
public class ReportRun {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private LocalDate reportDate;

	private String timezone;

	private String mediaId;

	private String granularity;

	private String runType;

	private Integer revision;

	private String dataStatus;

	private Integer rowCount;

	private LocalDateTime sourceWatermark;

	private String failureReason;

	private String dataOrigin;

	private LocalDateTime inputCutoffReceivedAt;

	private String inputSnapshotHash;

	private String ruleVersion;

	private String status;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

}
