package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_agreement_template")
public class AgreementTemplate {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String templateType;

	private String name;

	private Integer version;

	private String content;

	private String status;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
