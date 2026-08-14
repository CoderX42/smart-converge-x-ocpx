package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AdvertiserViews {

	private AdvertiserViews() {
	}

	public record Item(String id, String advertiserName, String username, String credentialConfigKey, String bidUrl,
			String macroParams, BigDecimal balance, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Option(String id, String name) {
	}

}
