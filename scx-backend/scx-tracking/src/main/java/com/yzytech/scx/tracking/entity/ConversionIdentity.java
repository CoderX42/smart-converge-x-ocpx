package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_conversion_identity")
public class ConversionIdentity {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String source;

	private String taskId;

	private String eventType;

	private String businessEventKeyHash;

	private String canonicalEventId;

	private LocalDateTime createTime;

}
