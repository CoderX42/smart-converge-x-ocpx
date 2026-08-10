package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class RoutePublishViews {

	private RoutePublishViews() {
	}

	public record CheckResult(String taskId, long taskConfigVersion, String overallStatus, LocalDateTime expiresAt,
			String checkToken, ConfigurationSummary configuration, List<CheckItem> items) {
	}

	public record ConfigurationSummary(long advertiserRevision, long productRevision, long productEventPolicyRevision,
			long appLinkRevision, long mediaAccountRevision, long mediaCredentialSecretRevision, long adapterRevision,
			int eventBindingCount, int dedupWindowSeconds, boolean exposureEnabled, boolean exposureAsClick,
			String rtaMode, int routeSchemaVersion, int eventSnapshotSchemaVersion) {
	}

	public record CheckItem(String code, String status, String message) {
	}

	public record PublishResult(String publishRequestId, String taskId, String monitorId, String routeVersionId,
			long routeVersion, String publicationStatus, String distributionStatus, boolean active,
			LocalDateTime publishedAt) {
	}

	public record VersionItem(String routeVersionId, String monitorId, long routeVersion, String routeKind,
			String publicationStatus, String acceptanceStatus, String distributionStatus, long taskConfigRevision,
			long mediaCredentialSecretRevision, int eventSnapshotSchemaVersion, LocalDateTime publishedAt,
			LocalDateTime acceptUntil) {
	}

	public record DistributionView(String taskId, String overallStatus, boolean active, int minimumRequiredAcks,
			int appliedCount, int failedCount, List<InstanceView> instances) {
	}

	public record InstanceView(String instanceId, long appliedVersion, String status, String errorCode,
			LocalDateTime ackedAt) {
	}

	public record AddressView(String taskId, String monitorId, long routeVersion, String template,
			LocalDateTime overlapUntil, String auditId) {
	}

	public record PausePreview(String taskId, long taskConfigVersion, int activeRouteCount, String impact,
			LocalDateTime expiresAt, String previewToken) {
	}

	public record PauseResult(String pauseRequestId, String taskId, String taskStatus, int deniedRouteCount,
			String distributionStatus, LocalDateTime pausedAt) {
	}

}
