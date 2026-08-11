package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_event_change_audit")
public class OcpxEventChangeAudit {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String objectType;

	private String objectId;

	private String channelId;

	private String taskId;

	private String action;

	private Long beforeVersion;

	private Long afterVersion;

	private String actor;

	private String changeSummary;

	private LocalDateTime createTime;

}
