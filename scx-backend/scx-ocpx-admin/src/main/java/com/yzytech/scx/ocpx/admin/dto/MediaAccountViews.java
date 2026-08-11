package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MediaAccountViews {

	private MediaAccountViews() {
	}

	public record ListItem(String id, String code, String channelId, String channelName, String adapterCode,
			String name, String description, String status, long configVersion, long credentialRevision,
			boolean documentConfigured, long credentialCount, long activeCredentialCount, long ownerCount,
			long publishedRouteCount, long republishRequiredCount, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Detail(String id, String code, String channelId, String channelName, String adapterCode, String name,
			String description, String documentId, long documentVersion, String status, long configVersion,
			long credentialRevision, String defaultCredentialId, ReferenceSummary references, LocalDateTime archivedAt,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Credential(String id, String loginType, String maskedIdentifier, boolean secretConfigured,
			long secretRevision, String description, String status, long version, String lastPreflightStatus,
			LocalDateTime lastPreflightAt, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Owner(String id, String userId, String displayName, String responsibility, String status,
			long version) {
	}

	public record ChannelOption(String id, String code, String name, String adapterCode, String authType, String status,
			boolean adapterAvailable) {
	}

	public record DocumentOption(String id, String type, long version, String summary) {
	}

	public record OwnerOption(String id, String name, String status) {
	}

	public record ReferenceSummary(long taskCount, long routeCount, long publishedRouteCount) {
	}

	public record Mutation(String id, String status, long configVersion, long credentialRevision,
			int republishRequiredCount) {
	}

	public record Check(String code, String level, String message) {
	}

	public record Preflight(String mode, boolean networkCalled, String status, List<Check> checks, long configVersion,
			long credentialRevision, LocalDateTime checkedAt) {
	}

}
