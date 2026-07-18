package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_click_dedup_result")
public class ClickDedupResult {

	@TableId
	private String clickEventId;

	private String ruleVersion;

	private String status;

	private String reasonCode;

	private String canonicalClickEventId;

	private LocalDateTime processedAt;

}
