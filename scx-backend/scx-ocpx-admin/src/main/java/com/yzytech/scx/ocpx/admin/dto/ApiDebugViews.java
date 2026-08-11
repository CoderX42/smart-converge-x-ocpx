package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApiDebugViews {

	private ApiDebugViews() {
	}

	public record Target(String monitorId, String monitorName, long routeVersion, String routeStatus, String channelId,
			String channelName, String channelCode, String downstreamAdapterCode, String advertiserId,
			String advertiserName, String upstreamAdapterCode, String upstreamTargetHost, String downstreamTargetHost,
			String billingMode, BigDecimal unitPrice) {
	}

	public record Preflight(String draftId, String confirmationPhrase, boolean billable, String environment,
			String riskSummary, String sideEffectSummary, Target target, LocalDateTime expiresAt) {
	}

	public record Authorization(String token, LocalDateTime expiresAt) {
	}

	public record Execution(String id, String mode, String scenario, String status, String monitorId, Long routeVersion,
			String requestMethod, String requestUrl, String requestHeaders, String requestBody, Integer responseStatus,
			String responseHeaders, String responseBody, String responseMime, Long responseSize, String responseSha256,
			Long durationMs, String riskSummary, String sideEffectSummary, String errorMessage, String createdBy,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record ExecutionLog(long sequence, String source, String level, String phase, String message,
			String detailJson, LocalDateTime createTime) {
	}

	public record DebugCase(String id, String caseName, String description, String mode, String scenario,
			String monitorId, String requestJson, String createdBy, String updatedBy, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record RecentLog(long sequence, String source, String timestamp, String level, String logger,
			String message) {
	}

	public record RecentLogs(List<RecentLog> records, long nextCursor, boolean truncated) {
	}

	public record EngineExecutionRequest(String executionId, String scenario, String monitorId, Long routeVersion,
			String method, String body, Map<String, String> headers) {
	}

	public record EngineExecutionAccepted(String executionId, String status, String eventId) {
	}

}
