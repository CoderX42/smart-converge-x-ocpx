package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CatalogViews {

	private CatalogViews() {
	}

	public record Channel(String id, String channelName, String uri, boolean secretConfigured, String macroParams,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Advertiser(String id, String advertiserName, String bidUrl, boolean secretConfigured,
			String macroParams, String account, BigDecimal balance, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record AppLink(String id, String advertiserId, String advertiserName, Integer linkPlatform, String linkName,
			String linkChannel, String linkUrl, String deeplink, String linkExt, String linkRemark,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Monitor(String id, String channelId, String channelName, String advertiserId, String advertiserName,
			String appLinkId, String appLinkName, String monitorName, String bizId, BigDecimal unitPrice, String remark,
			String showUrl, String clickUrl, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Deduction(String id, String channelId, String channelName, String appLinkId, String appLinkName,
			BigDecimal ratio, LocalDateTime createTime, LocalDateTime updateTime) {
	}

}
