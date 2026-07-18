package com.yzytech.scx.tracking.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttributionReviewView {

	private String id;

	private String mediaId;

	private String taskId;

	private String taskCode;

	private String taskName;

	private String conversionEventId;

	private String conversionEventType;

	private LocalDateTime conversionOccurredAt;

	private String clickEventId;

	private LocalDateTime clickOccurredAt;

	private String attributionStatus;

	private String attributionReason;

	private Boolean overLimit;

	private String reviewStatus;

	private String reviewReason;

	private String reviewedBy;

	private LocalDateTime reviewedAt;

	private LocalDateTime attributedAt;

	/**
	 * Latest callback job for this attribution, including a reduction correction when
	 * present.
	 */
	private String callbackActionType;

	private String callbackStatus;

	private Integer callbackAttempts;

	private String callbackLastErrorCode;

	private String callbackLastErrorMessage;

	private LocalDateTime callbackUpdatedAt;

}
