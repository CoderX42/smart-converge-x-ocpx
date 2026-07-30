package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_media_report_sync_run")
public class MediaReportSyncRun {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaAccountId;

	private String syncType;

	private LocalDateTime windowStart;

	private LocalDateTime windowEnd;

	private String cursorValue;

	private String requestHash;

	private String status;

	private Integer fetchedRows;

	private Integer persistedRows;

	private String errorCode;

	private String errorMessage;

	private String lockedBy;

	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private LocalDateTime lockedUntil;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	private LocalDateTime createTime;

}
