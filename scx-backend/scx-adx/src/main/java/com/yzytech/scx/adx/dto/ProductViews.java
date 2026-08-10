package com.yzytech.scx.adx.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ProductViews {

	private ProductViews() {
	}

	public record ListItem(String id, String advertiserId, String advertiserName, String code, String name,
			String packageName, String description, String status, long configVersion, long eventPolicyRevision,
			boolean logoAvailable, String logoUrl, long eventPolicyCount, long appLinkCount, long republishRequiredCount,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Detail(String id, String advertiserId, String advertiserName, String code, String name,
			String packageName, String description, String status, long configVersion, long eventPolicyRevision,
			boolean logoAvailable, String logoUrl, List<EventPolicy> eventPolicies, ReferenceSummary references,
			LocalDateTime archivedAt, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record EventPolicy(String eventDefinitionId, String eventCode, String eventName, int dailyLimit,
			long version) {
	}

	public record AdvertiserOption(String id, String code, String name) {
	}

	public record EventOption(String id, String eventCode, String eventName) {
	}

	public record ReferenceSummary(long appLinkCount, long routeCount) {
	}

	public record Mutation(String id, String status, long configVersion, long eventPolicyRevision,
			int republishRequiredCount) {
	}

}
