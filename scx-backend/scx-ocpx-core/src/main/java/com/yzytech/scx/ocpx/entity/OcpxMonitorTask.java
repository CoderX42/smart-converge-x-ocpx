package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_monitor_task")
public class OcpxMonitorTask {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String taskCode;

	private String taskName;

	private String advertiserId;

	private String promotionLinkId;

	private String mediaId;

	private String operatorUserId;

	private String status;

	private Long configVersion;

	private LocalDateTime archivedAt;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
