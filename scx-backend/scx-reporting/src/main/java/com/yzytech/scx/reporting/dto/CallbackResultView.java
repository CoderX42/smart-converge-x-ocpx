package com.yzytech.scx.reporting.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CallbackResultView {

	private String callbackJobId;

	private String attributionId;

	private String rawEventId;

	private String eventId;

	private String eventType;

	private LocalDateTime occurredAt;

	private String taskId;

	private String taskName;

	private String targetRef;

	private String actionType;

	private String status;

	private Integer attempts;

	private String lastErrorCode;

	private String lastErrorMessage;

	private LocalDateTime nextRetryAt;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private LocalDateTime completedAt;

}
