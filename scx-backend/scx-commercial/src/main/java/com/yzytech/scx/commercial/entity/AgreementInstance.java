package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_agreement_instance")
public class AgreementInstance {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String templateId;

	private Integer templateVersion;

	private String orgId;

	private String status;

	private LocalDateTime signedAt;

	private LocalDateTime expiredAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
