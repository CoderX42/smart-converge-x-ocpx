package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_event_outbox")
public class EventOutbox {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String aggregateId;

	private String eventType;

	private String payloadJson;

	private String status;

	private Integer attempts;

	private LocalDateTime nextRetryAt;

	private String lockedBy;

	private LocalDateTime lockedUntil;

	private String lastErrorCode;

	private String lastErrorMessage;

	private LocalDateTime createTime;

	private LocalDateTime sentAt;

}
