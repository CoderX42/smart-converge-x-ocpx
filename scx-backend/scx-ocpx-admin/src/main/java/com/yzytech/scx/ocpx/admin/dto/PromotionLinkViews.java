package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class PromotionLinkViews {

	private PromotionLinkViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String advertiserId;

		private String advertiserName;

		private String linkName;

		private Integer platform;

		private String linkChannel;

		private String linkUrl;

		private String deeplink;

		private String linkExt;

		private String remark;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AdvertiserOption {

		private String id;

		private String name;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String name;

	}

}
