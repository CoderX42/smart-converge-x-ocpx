package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_raw_event")
public class RawEvent {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String source;

	private String clientId;

	private String eventId;

	private String receiptId;

	private String taskId;

	private String eventType;

	private String businessEventKeyHash;

	private LocalDateTime occurredAt;

	private LocalDateTime receivedAt;

	private String devicePlatform;

	private String deviceIdType;

	private String deviceIdVersion;

	private String deviceKeyVersion;

	private String deviceKeyHash;

	private String payloadHash;

	private String payloadJson;

	private String status;

	private String processingStatus;

	private LocalDateTime processedAt;

	private String lockedBy;

	private LocalDateTime lockedUntil;

}
