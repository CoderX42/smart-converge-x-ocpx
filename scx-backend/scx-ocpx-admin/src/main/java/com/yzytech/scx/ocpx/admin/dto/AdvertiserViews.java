package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdvertiserViews {

	private AdvertiserViews() {
	}

	public record ListItem(String id, String advertiserName, String code, String adapterKey, Long adapterVersion,
			String maskedBidUrl, boolean credentialConfigured, Long credentialRevision, Long documentRevision,
			BigDecimal balance, String balanceStatus, String status, Long configVersion, boolean republishRequired,
			boolean integrationConfigured, LocalDateTime updateTime) {
	}

	public record Detail(String id, String advertiserName, String code, String adapterKey, Long adapterVersion, String bidUrl,
			String macroParams, String upstreamHostAllowlist, String accountMasked, boolean credentialConfigured,
			Long credentialRevision, String currentDocumentId, Long documentRevision, BigDecimal balance, String status,
			Long configVersion, int republishRequiredCount, boolean integrationConfigured, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record CredentialState(boolean configured, Long revision, LocalDateTime overlapUntil,
			int republishRequiredCount) {
	}

	public record Mutation(String id, Long configVersion, int republishRequiredCount) {
	}

	public record Document(String id, String documentType, String fileName, String contentType, Long fileSize,
			Long version, String scanStatus, String status, String summary, String createdBy, LocalDateTime createdAt,
			boolean current) {
	}

	public record AdapterCapability(String adapterKey, String adapterName, Long version, boolean requiresBidUrl,
			boolean supportsCallback, List<String> authTypes, List<String> eventVocabulary, Integer maxTimeoutMs) {
	}

}
