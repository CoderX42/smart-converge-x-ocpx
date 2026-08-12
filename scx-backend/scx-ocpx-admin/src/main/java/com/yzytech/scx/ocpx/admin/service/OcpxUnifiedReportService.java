package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxUnifiedReportService {

	private static final Set<String> GROUPS = Set.of("TIME", "ADVERTISER", "PROMOTION_LINK", "MEDIA", "MONITOR_TASK");

	private static final List<MetricColumn> METRICS = List.of(new MetricColumn("SHOW", "show_smt"),
			new MetricColumn("UNIQUE_SHOW", "uni_show_smt"), new MetricColumn("CLICK", "clk_smt"),
			new MetricColumn("UNIQUE_CLICK", "uni_clk_smt"), new MetricColumn("ACTIVATE", "activate"),
			new MetricColumn("EVOKE", "evoke"), new MetricColumn("LOGIN", "login"),
			new MetricColumn("REGISTER", "register"), new MetricColumn("PAYMENT", "payment"),
			new MetricColumn("CUSTOMER_ACQUISITION", "customer_acquisition"),
			new MetricColumn("NEXT_STAY", "next_stay"), new MetricColumn("VISITS", "visits"),
			new MetricColumn("AUTHENTICATION", "authentication"), new MetricColumn("PLACE_ORDER", "place_order"),
			new MetricColumn("FINISH_THE_ORDER", "finish_the_order"));

	private final JdbcTemplate jdbcTemplate;

	public UnifiedReportViews.Page query(UnifiedReportQuery input) {
		UnifiedReportQuery query = normalize(input);
		List<MetricRow> rows = aggregate(query, false);
		Map<String, UnifiedReportViews.Record> grouped = group(rows, query.groupBy());
		List<UnifiedReportViews.Record> all = new ArrayList<>(grouped.values());
		long start = Math.min(all.size(), (query.current() - 1) * query.size());
		long end = Math.min(all.size(), start + query.size());
		return new UnifiedReportViews.Page(List.copyOf(all.subList((int) start, (int) end)), query.current(),
				query.size(), all.size(), summarize(rows), LocalDateTime.now());
	}

	public UnifiedReportViews.Dashboard dashboard(UnifiedReportQuery input) {
		UnifiedReportQuery query = normalize(input);
		List<MetricRow> rows = aggregate(query, false);
		return new UnifiedReportViews.Dashboard(summarize(rows), ranking(query, "ADVERTISER"),
				ranking(query, "PROMOTION_LINK"), ranking(query, "MEDIA"), ranking(query, "MONITOR_TASK"),
				LocalDateTime.now());
	}

	public UnifiedReportViews.Options options() {
		return new UnifiedReportViews.Options(options("scx_ocpx_advertiser", "advertiser_code", "advertiser_name"),
				options("scx_ocpx_promotion_link", "link_code", "link_name"),
				options("scx_ocpx_media", "media_code", "media_name"),
				options("scx_ocpx_monitor_task", "task_code", "task_name"));
	}

	private List<UnifiedReportViews.Record> ranking(UnifiedReportQuery query, String groupBy) {
		UnifiedReportQuery ranked = new UnifiedReportQuery(query.startDate(), query.endDate(), groupBy,
				query.advertiserId(), query.promotionLinkId(), query.mediaId(), query.monitorTaskId(), 1, 10);
		return new ArrayList<>(group(aggregate(ranked, true), groupBy).values()).stream()
			.sorted(Comparator.comparingLong(this::totalCount).reversed())
			.limit(10)
			.toList();
	}

	private long totalCount(UnifiedReportViews.Record record) {
		return record.metrics().values().stream().mapToLong(UnifiedReportViews.MetricValue::count).sum();
	}

	private List<MetricRow> aggregate(UnifiedReportQuery query, boolean ranking) {
		Dimension dimension = Dimension.of(query.groupBy());
		StringBuilder sql = new StringBuilder("SELECT ").append(dimension.keySql())
			.append(",")
			.append(dimension.nameSql());
		for (MetricColumn metric : METRICS) {
			sql.append(",IFNULL(SUM(r.").append(metric.column()).append("),0)");
		}
		sql.append(",IFNULL(SUM(r.base_price),0) FROM scx_ocpx_report_mat r ")
			.append(dimension.joins())
			.append(" WHERE r.time_hour>=? AND r.time_hour<=?");
		List<Object> args = new ArrayList<>();
		args.add(hourKey(query.startDate(), "00"));
		args.add(hourKey(query.endDate(), "23"));
		filter(sql, args, "r.dsp_id", query.advertiserId());
		filter(sql, args, "r.link_id", query.promotionLinkId());
		filter(sql, args, "r.channel_id", query.mediaId());
		filter(sql, args, "r.monitor_id", query.monitorTaskId());
		sql.append(" GROUP BY ").append(dimension.keySql()).append(",").append(dimension.nameSql());
		if (ranking) {
			sql.append(" ORDER BY ");
			for (int index = 0; index < METRICS.size(); index++) {
				if (index > 0) {
					sql.append("+");
				}
				sql.append("SUM(r.").append(METRICS.get(index).column()).append(")");
			}
			sql.append(" DESC");
		}
		return jdbcTemplate.query(sql.toString(), (ResultSetExtractor<List<MetricRow>>) this::readMetricRows,
				args.toArray());
	}

	private List<MetricRow> readMetricRows(ResultSet resultSet) throws SQLException {
		List<MetricRow> rows = new ArrayList<>();
		while (resultSet.next()) {
			String key = resultSet.getString(1);
			String name = resultSet.getString(2);
			for (int index = 0; index < METRICS.size(); index++) {
				rows.add(new MetricRow(key, name, METRICS.get(index).code(), resultSet.getLong(index + 3),
						BigDecimal.ZERO));
			}
			BigDecimal amount = resultSet.getBigDecimal(METRICS.size() + 3)
				.divide(BigDecimal.valueOf(10_000_000), 2, RoundingMode.HALF_UP);
			rows.add(new MetricRow(key, name, "BASE_PRICE", 0, amount));
		}
		return rows;
	}

	private Map<String, UnifiedReportViews.Record> group(List<MetricRow> rows, String groupBy) {
		Map<String, Map<String, UnifiedReportViews.MetricValue>> metrics = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			metrics.computeIfAbsent(row.key(), ignored -> new LinkedHashMap<>())
				.put(row.metricCode(), new UnifiedReportViews.MetricValue(row.count(), row.amount()));
			names.putIfAbsent(row.key(), row.name());
		}
		Map<String, UnifiedReportViews.Record> result = new LinkedHashMap<>();
		metrics.forEach((key, value) -> result.put(key,
				new UnifiedReportViews.Record(key, groupBy, names.get(key), Map.copyOf(value))));
		return result;
	}

	private Map<String, UnifiedReportViews.MetricValue> summarize(List<MetricRow> rows) {
		Map<String, Long> counts = new LinkedHashMap<>();
		Map<String, BigDecimal> amounts = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			counts.merge(row.metricCode(), row.count(), Long::sum);
			amounts.merge(row.metricCode(), row.amount(), BigDecimal::add);
		}
		Map<String, UnifiedReportViews.MetricValue> result = new LinkedHashMap<>();
		counts.forEach((code, count) -> result.put(code, new UnifiedReportViews.MetricValue(count, amounts.get(code))));
		return Map.copyOf(result);
	}

	private List<UnifiedReportViews.Option> options(String table, String code, String name) {
		String sql = "SELECT id," + code + "," + name + ",status FROM " + table + " WHERE deleted=0 ORDER BY " + name;
		return jdbcTemplate.query(sql, (resultSet, rowNum) -> new UnifiedReportViews.Option(resultSet.getString(1),
				resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)));
	}

	private void filter(StringBuilder sql, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(" AND ").append(column).append("=?");
			args.add(value);
		}
	}

	private int hourKey(LocalDate date, String hour) {
		return Integer.parseInt(date.format(DateTimeFormatter.BASIC_ISO_DATE) + hour);
	}

	private UnifiedReportQuery normalize(UnifiedReportQuery query) {
		LocalDate end = query == null || query.endDate() == null ? LocalDate.now() : query.endDate();
		LocalDate start = query == null || query.startDate() == null ? end.minusDays(6) : query.startDate();
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new IllegalArgumentException("报表时间范围无效");
		}
		String groupBy = query == null || !StringUtils.hasText(query.groupBy()) ? "TIME"
				: query.groupBy().trim().toUpperCase();
		if (!GROUPS.contains(groupBy)) {
			throw new IllegalArgumentException("groupBy 仅支持 TIME/ADVERTISER/PROMOTION_LINK/MEDIA/MONITOR_TASK");
		}
		return new UnifiedReportQuery(start, end, groupBy, query == null ? null : query.advertiserId(),
				query == null ? null : query.promotionLinkId(), query == null ? null : query.mediaId(),
				query == null ? null : query.monitorTaskId(), query == null ? 1 : Math.max(1, query.current()),
				query == null ? 20 : Math.min(100, Math.max(1, query.size())));
	}

	private record MetricColumn(String code, String column) {
	}

	private record MetricRow(String key, String name, String metricCode, long count, BigDecimal amount) {
	}

	private record Dimension(String keySql, String nameSql, String joins) {

		static Dimension of(String groupBy) {
			return switch (groupBy) {
				case "ADVERTISER" ->
					new Dimension("r.dsp_id", "a.advertiser_name", "JOIN scx_ocpx_advertiser a ON a.id=r.dsp_id");
				case "PROMOTION_LINK" ->
					new Dimension("r.link_id", "p.link_name", "JOIN scx_ocpx_promotion_link p ON p.id=r.link_id");
				case "MEDIA" ->
					new Dimension("r.channel_id", "m.media_name", "JOIN scx_ocpx_media m ON m.id=r.channel_id");
				case "MONITOR_TASK" ->
					new Dimension("r.monitor_id", "t.task_name", "JOIN scx_ocpx_monitor_task t ON t.id=r.monitor_id");
				default -> new Dimension("r.time_hour",
						"DATE_FORMAT(STR_TO_DATE(CAST(r.time_hour AS CHAR),'%Y%m%d%H'),'%Y-%m-%d %H:00')", "");
			};
		}

	}

}
