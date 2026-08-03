package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.util.Map;

public final class RuntimeViews {

	private RuntimeViews() {
	}

	public record Overview(long pendingDeliveries, long retryDeliveries, long deadDeliveries, long successfulDeliveries,
			long ingressDlq, long feedbackStreamPending, Map<String, Long> byDirection) {
	}

	public record Delivery(String id, String eventId, String sourceEventId, String direction, String adapterCode,
			String status, Integer attemptCount, LocalDateTime nextRetryAt, String lastError, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record IngressDlq(String id, Integer partitionNo, String streamRecordId, String eventId, String reason,
			String status, LocalDateTime createTime, LocalDateTime replayedAt) {
	}

}
