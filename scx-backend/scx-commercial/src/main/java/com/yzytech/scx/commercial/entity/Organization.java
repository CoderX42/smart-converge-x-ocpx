package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_organization")
public class Organization {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String name;

	private String orgType;

	private String parentOrgId;

	private String status;

	private String legalPerson;

	private String contactName;

	private String contactPhone;

	private String contactEmail;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
