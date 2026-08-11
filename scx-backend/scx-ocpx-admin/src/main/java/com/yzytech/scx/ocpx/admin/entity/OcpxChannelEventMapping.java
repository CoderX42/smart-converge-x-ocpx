package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_channel_event_mapping")
public class OcpxChannelEventMapping {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelId;

	private String adapterCode;

	private String externalCode;

	private String direction;

	private String eventDefinitionId;

	private String parameterTemplateCode;

	private String successRuleCode;

	private String status;

	private Long configVersion;

	private String mappingState;

	private String createRequestKey;

	private String createRequestHash;

	private String remark;

	private LocalDateTime archivedAt;

	private String createBy;

	private String updateBy;

	private Boolean deleted;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
