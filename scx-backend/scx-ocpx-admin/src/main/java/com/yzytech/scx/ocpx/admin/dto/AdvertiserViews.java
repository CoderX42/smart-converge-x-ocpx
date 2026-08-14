package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AdvertiserViews {

	private AdvertiserViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String advertiserName;

		private String username;

		private String credentialConfigKey;

		private String bidUrl;

		private String macroParams;

		private BigDecimal balance;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String name;

	}

}
