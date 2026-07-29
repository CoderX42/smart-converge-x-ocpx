package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_creative_review_log")
public class CreativeReviewLog {

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
