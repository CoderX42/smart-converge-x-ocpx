package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_conversion_dedup_result")
public class ConversionDedupResult {

	@TableId
	private String conversionEventId;

	private String status;

	private String reasonCode;

	private String canonicalEventId;

	private LocalDateTime processedAt;

}
