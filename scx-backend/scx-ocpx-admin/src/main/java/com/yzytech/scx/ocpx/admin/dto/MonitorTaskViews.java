package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MonitorTaskViews {

	private MonitorTaskViews() {
	}

	public record Item(String id, String taskCode, String taskName, String advertiserId, String advertiserName,
			String promotionLinkId, String promotionLinkName, String mediaId, String mediaName, String operatorUserId,
			String status, long configVersion, String showUrl, String clkUrl, BigDecimal unitPrice, String bizId,
			String remark, LocalDateTime archivedAt, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Mutation(String id, String status, long configVersion) {
	}

}
