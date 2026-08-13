package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AdvertiserViews {

	private AdvertiserViews() {
	}

	public record Item(String id, String advertiserName, String advertiserCode, String adapterCode,
			String credentialConfigKey, long credentialVersion, String bidUrl, String macroParams,
			String upstreamHostAllowlist, String status, long configVersion, BigDecimal balance,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Mutation(String id, String status, long configVersion) {
	}

}
