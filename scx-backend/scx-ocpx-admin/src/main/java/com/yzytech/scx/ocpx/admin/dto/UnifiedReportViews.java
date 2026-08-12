package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class UnifiedReportViews {

	private UnifiedReportViews() {
	}

	public record MetricValue(long count, BigDecimal amount) {
	}

	public record Record(String dimensionKey, String dimensionType, String dimensionName,
			Map<String, MetricValue> metrics) {
	}

	public record Page(List<Record> records, long current, long size, long total,
			Map<String, MetricValue> summary, LocalDateTime asOf) {
	}

	public record Option(String id, String code, String label, String status) {
	}

	public record Options(List<Option> advertisers, List<Option> promotionLinks, List<Option> media,
			List<Option> monitorTasks) {
	}

	public record Dashboard(Map<String, MetricValue> summary, List<Record> advertiserRanking,
			List<Record> promotionLinkRanking, List<Record> mediaRanking, List<Record> monitorTaskRanking,
			LocalDateTime asOf) {
	}

}
