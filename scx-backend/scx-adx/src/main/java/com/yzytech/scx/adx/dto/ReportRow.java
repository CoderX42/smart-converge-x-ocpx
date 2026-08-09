package com.yzytech.scx.adx.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportRow {

	private String timeBucket;

	private String channelId;

	private String channelName;

	private String monitorId;

	private String monitorName;

	private String advertiserId;

	private String advertiserName;

	private String appLinkId;

	private String appLinkName;

	private BigDecimal deductionRatio;

	private Long impressions;

	private Long clicks;

	private Long eligibleClickCount;

	private Long deduplicatedClickCount;

	private Long skippedClickCount;

	private Long classifiedClickCount;

	private BigDecimal clickRate;

	private BigDecimal costAmount;

	private BigDecimal revenueAmount;

	private Long activateCount;

	private BigDecimal activateRate;

	private Long firstActivateCount;

	private BigDecimal firstActivateRate;

	private Long reactivationCount;

	private BigDecimal reactivationRate;

	private Long evokeCount;

	private Long loginCount;

	private Long registerCount;

	private Long paymentCount;

	private Long acquisitionCount;

	private Long nextStayCount;

	private Long retention3dCount;

	private Long retention4dCount;

	private Long retention5dCount;

	private Long retention6dCount;

	private Long retention7dCount;

	private Long visitCount;

	private Long authenticationCount;

	private Long orderCount;

	private Long firstOrderCount;

	private Long finishOrderCount;

	private Long rawConversionCount;

	private Long forwardedConversionCount;

	private Long deductedConversionCount;

	private Long deliveryFailureCount;

}
