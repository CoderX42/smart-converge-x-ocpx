package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_click_dedup_guard")
public class ClickDedupGuard {

	@TableId
	private String guardKey;

	private String taskId;

	private String deviceIdType;

	private String deviceKeyHash;

	private String ruleVersion;

	private String canonicalClickEventId;

	private LocalDateTime windowStartedAt;

	private LocalDateTime updateTime;

}
