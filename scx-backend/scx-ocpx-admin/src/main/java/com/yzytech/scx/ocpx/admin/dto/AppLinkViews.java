package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AppLinkViews {

	private AppLinkViews() {
	}

	public record ListItem(String id, String bizCode, String advertiserId, String advertiserName, String productId,
			String productCode, String productName, String documentId, long documentVersion, String linkName,
			String linkType, boolean redirectEnabled, String templateMasked, String deeplinkMasked,
			String downloadMasked, String templateHost, List<String> macroSet, String status, String mappingStatus,
			long configVersion, long republishRequiredCount, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Detail(String id, String bizCode, String advertiserId, String advertiserName, String productId,
			String productCode, String productName, String documentId, long documentVersion, String linkName,
			String linkType, boolean redirectEnabled, String templateMasked, String deeplinkMasked,
			String downloadMasked, boolean templateConfigured, boolean deeplinkConfigured, boolean downloadConfigured,
			String templateHost, List<String> macroSet, String validationStatus, String description, String status,
			String mappingStatus, long configVersion, ReferenceSummary references, LocalDateTime archivedAt,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record ProductOption(String id, String advertiserId, String advertiserName, String code, String name) {
	}

	public record DocumentOption(String id, String documentType, String fileName, long version, String status,
			String scanStatus, boolean current) {
	}

	public record ReferenceSummary(long monitorCount, long publishedRouteCount, long deductionRuleCount,
			long republishRequiredCount) {
	}

	public record ValidationReport(String kind, String status, boolean networkCalled, String normalizedMaskedUrl,
			String host, String fingerprint, List<String> macroSet, List<String> issues) {
	}

	public record Mutation(String id, String status, String mappingStatus, long configVersion,
			int republishRequiredCount) {
	}

}
