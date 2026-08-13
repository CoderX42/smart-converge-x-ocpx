package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;

public final class DeductionRuleViews {

	private DeductionRuleViews() {
	}

	public record Item(String id, String mediaId, String mediaName, String promotionLinkId, String promotionLinkName,
			Double ratio, LocalDateTime createTime, LocalDateTime updateTime) {
	}

}
