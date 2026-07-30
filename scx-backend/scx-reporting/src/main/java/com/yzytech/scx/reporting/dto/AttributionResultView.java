package com.yzytech.scx.reporting.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttributionResultView {

	private String attributionId;

	private String rawEventId;

	private String eventId;

	private String eventType;

	private LocalDateTime occurredAt;

	private String taskId;

	private String taskName;

	private String status;

	private String reasonCode;

	private Integer revision;

	private Boolean overLimit;

	private String reviewStatus;

	private String reviewReason;

	private String clickEventId;

	private LocalDateTime attributedAt;

}
