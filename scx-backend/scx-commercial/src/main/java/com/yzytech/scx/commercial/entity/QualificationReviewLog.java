package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_qualification_review_log")
public class QualificationReviewLog {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String caseId;

	private String fromStatus;

	private String toStatus;

	private String operatorId;

	private String operatorName;

	private String comment;

	private LocalDateTime createdAt;

}
