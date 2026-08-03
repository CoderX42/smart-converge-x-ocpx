package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LogDataProjection(LocalDateTime timeBucket, String channelId, String advertiserId, String appLinkId,
		String monitorId, long impressions, long clicks, long rawConversions, long forwardedConversions,
		long deductedConversions, long deliveryFailures, BigDecimal costAmount, BigDecimal revenueAmount) {
}
