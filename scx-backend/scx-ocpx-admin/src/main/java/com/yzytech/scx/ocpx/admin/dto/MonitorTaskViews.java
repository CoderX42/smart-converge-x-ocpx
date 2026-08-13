package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MonitorTaskViews {

	private MonitorTaskViews() {
	}

	public record Item(String id, String monitorName, String advertiserId, String advertiserName,
			String promotionLinkId, String promotionLinkName, Integer platform, String mediaId, String mediaName,
			String showUrl, String clkUrl, BigDecimal unitPrice, String bizId, String remark, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

}
