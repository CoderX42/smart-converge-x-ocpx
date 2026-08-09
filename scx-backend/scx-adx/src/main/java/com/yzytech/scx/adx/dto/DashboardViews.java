package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DashboardViews {

	private DashboardViews() {
	}

	public record Filters(LocalDate startDate, LocalDate endDate, String channelId, String advertiserId,
			String appLinkId, String monitorId) {
	}

	public record ComparisonMetric(long value, long comparisonValue, BigDecimal changeRate, String comparisonLabel) {
	}

	public record Highlights(ComparisonMetric todayClicks, ComparisonMetric todayActivations,
			ComparisonMetric yesterdayRetentionD2, ComparisonMetric todayFirstActivations,
			ComparisonMetric todayPayments) {
	}

	public record MetricSet(long impressions, long clicks, long eligibleClicks, long deduplicatedClicks,
			long skippedClicks, long classifiedClicks, BigDecimal costAmount, long activations, long firstActivations,
			long reactivations, long registrations, long payments, long retentionD2, long retentionD3, long retentionD4,
			long retentionD5, long retentionD6, long retentionD7, long rawConversions, long forwardedConversions,
			long deductedConversions, long deliveryFailures, BigDecimal activationRate, BigDecimal firstActivationRate,
			BigDecimal reactivationRate) {
	}

	public record DailyPoint(LocalDate reportDate, MetricSet metrics) {
	}

	public record FunnelStage(String key, String label, long value, BigDecimal previousRate) {
	}

	public record RetentionPoint(LocalDate reportDate, String day, long value) {
	}

	public record DataCoverage(boolean clickQualityComplete, long totalClicks, long classifiedClicks, String rateStatus,
			String retentionBasis) {
	}

	public record ConfigurationCoverage(long activeChannels, long activeAdvertisers, long activeAppLinks,
			long publishedRoutes, long draftRoutes, long abnormalRoutes) {
	}

	public record Summary(String timezone, LocalDateTime generatedAt, LocalDateTime dataUpdatedAt, Filters filters,
			DataCoverage dataCoverage, Highlights highlights, MetricSet totals, List<DailyPoint> trend,
			List<FunnelStage> funnel, List<RetentionPoint> retention, ConfigurationCoverage configuration) {
	}

	public record AccountSummary(String currency, BigDecimal totalBalance, BigDecimal rechargeAmount,
			BigDecimal cpcSpend, BigDecimal cpaSpend, long rejectedCharges, long cpaRejectedCharges,
			LocalDateTime generatedAt) {
	}

}
