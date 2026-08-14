package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MonitorTaskViews {

	private MonitorTaskViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String monitorName;

		private String advertiserId;

		private String advertiserName;

		private String promotionLinkId;

		private String promotionLinkName;

		private Integer platform;

		private String mediaId;

		private String mediaName;

		private String showUrl;

		private String clkUrl;

		private BigDecimal unitPrice;

		private String bizId;

		private String remark;

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
