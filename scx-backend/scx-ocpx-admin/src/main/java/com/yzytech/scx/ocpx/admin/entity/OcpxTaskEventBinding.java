package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_task_event_binding")
public class OcpxTaskEventBinding {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String taskId;

	private String channelId;

	private String eventDefinitionId;

	private String channelEventMappingId;

	private Integer callbackRatioBasisPoints;

	private Long limitValue;

	private String limitWindow;

	private String limitTimezone;

	private Boolean overLimitDelivery;

	private String samplingAlgorithm;

	private String sourceType;

	private String sourceId;

	private Long sourceRevision;

	private String status;

	private Long configVersion;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
