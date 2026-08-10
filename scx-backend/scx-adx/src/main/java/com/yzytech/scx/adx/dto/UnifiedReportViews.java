package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class UnifiedReportViews {

	private UnifiedReportViews() {
	}

	public record MetricDefinition(String code, String label, String kind, String numerator, String denominator,
			String status, boolean sensitive) {
	}

	public record MetricValue(BigDecimal value, BigDecimal numerator, BigDecimal denominator, String status) {
	}

	public record Record(String dimensionKey, String dimensionType, Map<String, Object> dimensions,
			Map<String, MetricValue> metrics, String qualityStatus, String finalityStatus, LocalDateTime asOf) {
	}

	public record Summary(Map<String, MetricValue> metrics, String label) {
	}

	public record Page(List<Record> records, long current, long size, long total, Summary pageSummary,
			Summary querySummary, LocalDateTime asOf, String timezone, String freshnessStatus,
			String metricDefinitionsVersion, List<MetricDefinition> metricDefinitions) {
	}

	public record Option(String id, String label, String status) {
	}

	public record Options(List<Option> advertisers, List<Option> products, List<Option> appLinks, List<Option> tasks,
			List<Option> channels, List<Option> mediaAccounts) {
	}

	public record ExportTask(String id, String type, String status, LocalDateTime expiresAt, String errorCode,
			LocalDateTime createTime) {
	}

}
