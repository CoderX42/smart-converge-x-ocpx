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

	private BigDecimal clickRate;

	private BigDecimal costAmount;

	private BigDecimal revenueAmount;

	private Long activateCount;

	private Long evokeCount;

	private Long loginCount;

	private Long registerCount;

	private Long paymentCount;

	private Long acquisitionCount;

	private Long nextStayCount;

	private Long visitCount;

	private Long authenticationCount;

	private Long orderCount;

	private Long finishOrderCount;

	private Long rawConversionCount;

	private Long forwardedConversionCount;

	private Long deductedConversionCount;

	private Long deliveryFailureCount;

}
