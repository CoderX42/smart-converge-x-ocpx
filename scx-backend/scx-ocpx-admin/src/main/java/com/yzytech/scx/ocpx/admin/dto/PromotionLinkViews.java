package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;

public final class PromotionLinkViews {

	private PromotionLinkViews() {
	}

	public record Item(String id, String advertiserId, String advertiserName, String linkName, Integer platform,
			String linkChannel, String linkUrl, String deeplink, String linkExt, String remark, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record AdvertiserOption(String id, String name) {
	}

}
