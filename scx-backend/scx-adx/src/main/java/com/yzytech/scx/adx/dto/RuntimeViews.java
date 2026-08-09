package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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

	public record Event(String id, String eventId, String parentEventId, String direction, String eventType,
			String sourceEventCode, String conversionEventCode, String mediaEventCode, String channelId,
			String channelName, String advertiserId, String advertiserName, String monitorId, String monitorName,
			String status, BigDecimal amount, String amountRaw, BigDecimal cpaChargeAmount, String dailyLimitResult,
			LocalDateTime occurredAt, LocalDateTime receivedAt) {
	}

	public record TimelineItem(String stage, String status, String referenceId, String detail,
			LocalDateTime occurredAt) {
	}

	public record DeliveryAttempt(String id, String jobId, Integer attemptNo, String status, Integer httpStatus,
			String errorMessage, String requestMethod, String targetHost, String targetPath, String requestSummary,
			String responseExcerpt, Long durationMs, LocalDateTime createTime) {
	}

	public record IngressAudit(String id, String requestId, String channelCode, String eventId, String routeReference,
			String resultStatus, Integer httpStatus, String reason, String payloadHash, String clientId,
			String keyVersion, String remoteIpMasked, Long durationMs, LocalDateTime createTime) {
	}

}
