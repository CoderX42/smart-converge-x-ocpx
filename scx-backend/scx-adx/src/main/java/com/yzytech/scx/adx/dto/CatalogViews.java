package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CatalogViews {

	private CatalogViews() {
	}

	public record Channel(String id, String channelCode, String channelName, String adapterCode, String authType,
			Integer keyVersion, String uri, boolean secretConfigured, String macroParams, String callbackHostAllowlist,
			String status, Long configVersion, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Advertiser(String id, String advertiserName, String adapterCode, String bidUrl,
			boolean secretConfigured, String macroParams, String upstreamHostAllowlist, String account,
			BigDecimal balance, String status, Long configVersion, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record AppLink(String id, String advertiserId, String advertiserName, Integer linkPlatform, String linkName,
			String linkChannel, String linkUrl, String deeplink, String linkExt, String linkRemark,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Monitor(String id, String channelId, String channelName, String advertiserId, String advertiserName,
			String appLinkId, String appLinkName, String monitorName, String bizId, BigDecimal unitPrice, String remark,
			String showUrl, String clickUrl, String upstreamFeedbackUrl, String effectiveUpstreamFeedbackUrl,
			String upstreamFeedbackSource, String downstreamCallbackUrl, Integer clickDedupWindowSeconds,
			String routeStatus, Long configVersion, String rtaMode, String billingMode, LocalDateTime publishedAt,
			LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record Deduction(String id, String channelId, String channelName, String appLinkId, String appLinkName,
			BigDecimal ratio, Long configVersion, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Adapter(String adapterCode, String adapterName, String direction, String status,
			String capabilitiesJson) {
	}

	/** 不含真实密钥的渠道接入信息，可直接展示和复制到 Apifox。 */
	public record OcpxIntegration(String monitorId, String monitorName, String routeStatus, String schemaVersion,
			String feedbackMethod, String feedbackUrl, String authType, Integer keyVersion, boolean secretConfigured,
			List<String> requiredHeaders, String signatureFormula, String requestExample, String callbackPathPattern,
			String upstreamAdapterCode, String downstreamAdapterCode, String postmanCollectionPath) {
	}

	public record AccountMutation(String advertiserId, String ledgerId, BigDecimal balance, boolean duplicate) {
	}

	public record AccountEntry(String id, String advertiserId, BigDecimal amount, String currency, String mode,
			String status, String reason, BigDecimal balanceBefore, BigDecimal balanceAfter, String operatorId,
			String remark, LocalDateTime createTime) {
	}

}
