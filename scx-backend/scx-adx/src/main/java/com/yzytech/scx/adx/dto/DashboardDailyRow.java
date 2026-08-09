package com.yzytech.scx.adx.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DashboardDailyRow {

	private LocalDate reportDate;

	private Long impressions;

	private Long clicks;

	private Long eligibleClicks;

	private Long deduplicatedClicks;

	private Long skippedClicks;

	private Long classifiedClicks;

	private BigDecimal costAmount;

	private Long activates;

	private Long firstActivations;

	private Long reactivations;

	private Long registrations;

	private Long payments;

	private Long retentionD2;

	private Long retentionD3;

	private Long retentionD4;

	private Long retentionD5;

	private Long retentionD6;

	private Long retentionD7;

	private Long rawConversions;

	private Long forwardedConversions;

	private Long deductedConversions;

	private Long deliveryFailures;

	private LocalDateTime dataUpdatedAt;

}
