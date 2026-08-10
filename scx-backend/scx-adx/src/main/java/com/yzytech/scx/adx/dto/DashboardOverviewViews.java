package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DashboardOverviewViews {

	private DashboardOverviewViews() {
	}

	public record MetricCard(String metric, String label, UnifiedReportViews.MetricValue current,
			UnifiedReportViews.MetricValue comparison, BigDecimal changeRate, String comparisonLabel) {
	}

	public record TrendPoint(String bucket, Map<String, UnifiedReportViews.MetricValue> metrics, String finalityStatus,
			LocalDateTime asOf) {
	}

	public record RankingItem(String dimensionKey, String objectId, Map<String, UnifiedReportViews.MetricValue> metrics,
			String finalityStatus, LocalDateTime asOf) {
	}

	public record Ranking(String groupBy, List<RankingItem> records) {
	}

	public record Section<T>(String status, T data, String errorCode, String traceId) {

		public static <T> Section<T> ready(T data) {
			return new Section<>("READY", data, null, null);
		}

		public static <T> Section<T> empty(T data) {
			return new Section<>("EMPTY", data, null, null);
		}

		public static <T> Section<T> failed(String status, String errorCode, String traceId) {
			return new Section<>(status, null, errorCode, traceId);
		}
	}

	public record Overview(LocalDate businessDate, LocalDate startDate, LocalDate endDate, String timezone,
			LocalDateTime asOf, String freshnessStatus, String metricDefinitionsVersion, long scopeRevision,
			boolean cacheHit, boolean partialFailure, List<UnifiedReportViews.MetricDefinition> metricDefinitions,
			Section<List<MetricCard>> cards, Section<List<TrendPoint>> trend, Section<Ranking> productRanking,
			Section<Ranking> taskRanking, Section<Ranking> mediaRanking) {
	}

}
