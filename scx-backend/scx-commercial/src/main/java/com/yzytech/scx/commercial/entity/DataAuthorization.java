package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_data_authorization")
public class DataAuthorization {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String grantorOrgId;

	private String granteeOrgId;

	private String authorizationType;

	private String scope;

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
