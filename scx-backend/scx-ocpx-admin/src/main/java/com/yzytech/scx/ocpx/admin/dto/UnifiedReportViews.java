package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class UnifiedReportViews {

	private UnifiedReportViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MetricValue {

		private long count;

		private BigDecimal amount;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Row {

		private String dimensionKey;

		private String dimensionType;

		private String dimensionName;

		private Map<String, MetricValue> metrics;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page {

		private List<Row> records;

		private long current;

		private long size;

		private long total;

		private Map<String, MetricValue> summary;

		private LocalDateTime asOf;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String label;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Options {

		private List<Option> advertisers;

		private List<Option> promotionLinks;

		private List<Option> media;

		private List<Option> monitorTasks;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Dashboard {

		private Map<String, MetricValue> summary;

		private List<Row> advertiserRanking;

		private List<Row> promotionLinkRanking;

		private List<Row> mediaRanking;

		private List<Row> monitorTaskRanking;

		private LocalDateTime asOf;

	}

}
