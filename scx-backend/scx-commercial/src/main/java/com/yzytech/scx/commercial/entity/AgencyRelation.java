package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_agency_relation")
public class AgencyRelation {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String parentAgencyOrgId;

	private String childOrgId;

	private String relationType;

	private String authorizedScopes;

	private String status;

	private LocalDate startDate;

	private LocalDate endDate;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
