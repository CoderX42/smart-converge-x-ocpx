package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RuntimeViews {

	private RuntimeViews() {
	}

	public record Overview(long accepted, long succeeded, long failed, long dlq, long replayQueued,
			long replayTerminal, String replayWorkerStatus, LocalDateTime asOf, String freshnessStatus,
			String traceId, Map<String, Long> byFactType, Map<String, Long> byFailureClass) {
	}

	public record PageResult<T>(List<T> records, long total, long current, long size, LocalDateTime asOf,
			String freshnessStatus, String traceId, List<String> qualityFlags) {
	}

	public record Fact(String id, String factType, String direction, String acceptanceStatus, String processingStatus,
			String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, String operatorUserId, String routeId, String routeVersionId, Long routeVersion,
			String eventCode, String traceId, String requestIdMasked, String correlationIdMasked,
			String externalClickIdMasked, String mediaTaskIdMasked, String summaryCode, String dimensionStatus,
			String qualityStatus, LocalDateTime eventTime, LocalDateTime receivedTime, boolean hasBody) {
	}

	public record Failure(String id, String sourceFactId, String sourceType, String failureClass, String errorCode,
			boolean replayable, long failureRevision, String taskId, String advertiserId, String channelId,
			String mediaAccountId, String routeId, String routeVersionId, Long routeVersion, String traceId,
			String summaryFingerprint, String dimensionStatus, LocalDateTime eventTime, LocalDateTime receivedTime,
			boolean hasBody) {
	}

	public record Timeline(String executionId, Fact fact, Failure failure, List<Attempt> attempts,
			List<ReplayStatus> replays, String traceId) {
	}

	public record Attempt(String id, int attemptNo, String status, String errorClass, String errorCode,
			String summaryFingerprint, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime nextRetryAt) {
	}

	public record ReplayPreview(String failureId, long failureRevision, String taskId, String routeVersionId,
			long routeVersion, String failureClass, String errorCode, boolean replayable, String mode,
			String externalEffect, String billingEffect, String workerStatus, LocalDateTime expiresAt,
			String previewToken, String traceId, List<String> blockers) {
	}

	public record ReplayStatus(String requestId, String commandId, String failureId, String taskId,
			String routeVersionId, long routeVersion, String mode, String status, int attemptCount, int maxAttempts,
			String lastErrorClass, String lastErrorCode, LocalDateTime nextAttemptAt, LocalDateTime leaseUntil,
			LocalDateTime createTime, LocalDateTime completedAt, String workerStatus) {
	}

}
