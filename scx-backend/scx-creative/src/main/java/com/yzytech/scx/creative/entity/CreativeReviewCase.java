package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_creative_review_case")
public class CreativeReviewCase {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String assetId;

	private String revisionId;

	private String status;

	private LocalDateTime submittedAt;

	private LocalDateTime reviewedAt;

	private String reviewerId;

	private String reviewComment;

	private String virusScanStatus;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
