package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_attribution_result")
public class AttributionResult {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String conversionEventId;

	private Integer revision;

	private String clickEventId;

	private String ruleVersion;

	private String status;

	private String reasonCode;

	private Boolean overLimit;

	private String reviewStatus;

	private String reviewReason;

	private String reviewedBy;

	private LocalDateTime reviewedAt;

	private Boolean currentFlag;

	private String supersedesId;

	private LocalDateTime attributedAt;

}
