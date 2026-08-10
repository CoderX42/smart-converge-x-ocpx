package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class TaskViews {

	private TaskViews() {
	}

	public record ListItem(String id, String bizCode, String taskName, String advertiserId, String advertiserName,
			String productId, String productName, String clickAppLinkId, String clickAppLinkName,
			String exposureAppLinkId, String exposureAppLinkName, String channelId, String channelName,
			String mediaAccountId, String mediaAccountName, String mediaCredentialId,
			long mediaCredentialSecretRevision, String operatorUserId, String operatorName, boolean exposureEnabled,
			boolean exposureAsClick, int dedupWindowSeconds, String rtaMode, String status, long configVersion,
			boolean republishRequired, long publishedTaskVersion, ReferenceSummary references, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record Detail(String id, String bizCode, String taskName, String advertiserId, String advertiserName,
			String productId, String productName, String clickAppLinkId, String clickAppLinkName,
			String exposureAppLinkId, String exposureAppLinkName, String channelId, String channelName,
			String mediaAccountId, String mediaAccountName, String mediaCredentialId,
			long mediaCredentialSecretRevision, String operatorUserId, String operatorName, boolean exposureEnabled,
			boolean exposureAsClick, int dedupWindowSeconds, String rtaMode, String description, String status,
			long configVersion, boolean republishRequired, String republishReason, long publishedTaskVersion,
			ReferenceSummary references, LocalDateTime archivedAt, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record ReferenceSummary(long monitorCount, long publishedMonitorCount, long republishRequiredCount) {
	}

	public record Options(List<AdvertiserOption> advertisers, List<ProductOption> products,
			List<AppLinkOption> appLinks, List<ChannelOption> channels, List<MediaAccountOption> mediaAccounts,
			List<CredentialOption> credentials, List<OperatorOption> operators) {
	}

	public record AdvertiserOption(String id, String name) {
	}

	public record ProductOption(String id, String advertiserId, String name) {
	}

	public record AppLinkOption(String id, String advertiserId, String productId, String name, String linkType) {
	}

	public record ChannelOption(String id, String name) {
	}

	public record MediaAccountOption(String id, String channelId, String name, String defaultCredentialId) {
	}

	public record CredentialOption(String id, String mediaAccountId, String label, long secretRevision,
			String status) {
	}

	public record OperatorOption(String id, String displayName) {
	}

	public record Mutation(String id, String status, long configVersion, boolean republishRequired,
			int affectedMonitorCount) {
	}

}
