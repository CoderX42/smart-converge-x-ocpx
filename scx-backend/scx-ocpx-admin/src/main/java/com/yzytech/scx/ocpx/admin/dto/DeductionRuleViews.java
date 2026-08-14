package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class DeductionRuleViews {

	private DeductionRuleViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String mediaId;

		private String mediaName;

		private String promotionLinkId;

		private String promotionLinkName;

		private Double ratio;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

}
