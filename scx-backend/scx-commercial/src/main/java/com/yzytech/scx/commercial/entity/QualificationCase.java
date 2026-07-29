package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_qualification_case")
public class QualificationCase {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String caseType;

	private String status;

	private LocalDateTime submittedAt;

	private LocalDateTime reviewedAt;

	private LocalDateTime expiredAt;

	private String reviewerId;

	private String reviewComment;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
