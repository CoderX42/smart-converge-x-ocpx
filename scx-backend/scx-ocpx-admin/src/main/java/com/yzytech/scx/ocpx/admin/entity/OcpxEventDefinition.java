package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_event_definition")
public class OcpxEventDefinition {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String category;

	private String channelId;

	private String eventCode;

	private String eventName;

	private String direction;

	private String standardType;

	private Boolean billingEligible;

	private String subjectType;

	private String status;

	private Integer sortOrder;

	private String remark;

	private Boolean builtIn;

	private Long configVersion;

	private String mappingState;

	private String createRequestKey;

	private String createRequestHash;

	private LocalDateTime archivedAt;

	private String createBy;

	private String updateBy;

	private Boolean deleted;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
