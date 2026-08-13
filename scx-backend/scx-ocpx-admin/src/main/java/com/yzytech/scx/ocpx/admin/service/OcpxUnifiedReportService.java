package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportViews;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.util.OcpxCsv;
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
import java.util.Arrays;
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

	private static final List<String> SUMMARY_ACTIONS = List.of("activate", "evoke", "login", "register", "payment",
			"customer_acquisition", "next_stay", "visits", "authentication", "place_order", "finish_the_order");

	private static final List<String> SUMMARY_CSV_HEADER = List.of("报告时间", "媒体名称", "监测名称", "广告主名称", "链接名称",
			"链接扣量", "去重展示数", "去重点击数", "点击率", "支出", "激活", "唤起", "登录", "注册", "付费", "获客", "次留", "访问",
			"认证", "下单", "完单");

	private static final Map<String, String> SUMMARY_SORT_COLUMNS = Map.ofEntries(Map.entry("report_time", "reportTime"),
			Map.entry("uni_show_smt", "uni_show_smt"), Map.entry("uni_clk_smt", "uni_clk_smt"),
			Map.entry("click_rate", "clickRate"), Map.entry("base_price", "base_price"), Map.entry("activate", "activate"),
			Map.entry("evoke", "evoke"), Map.entry("login", "login"), Map.entry("register", "register"),
			Map.entry("payment", "payment"), Map.entry("customer_acquisition", "customer_acquisition"),
			Map.entry("next_stay", "next_stay"), Map.entry("visits", "visits"),
			Map.entry("authentication", "authentication"), Map.entry("place_order", "place_order"),
			Map.entry("finish_the_order", "finish_the_order"));

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

	public SummaryReportViews.Page summary(SummaryReportQuery input) {
		SummaryReportQuery query = normalizeSummary(input);
		SummarySql sql = summarySql(query);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + sql.statSql() + ") AS stat", Long.class,
				sql.args().toArray());
		StringBuilder pageSql = new StringBuilder(sql.selectSql());
		appendSummaryOrder(pageSql, query);
		pageSql.append(" LIMIT ? OFFSET ?");
		List<Object> args = new ArrayList<>(sql.args());
		args.add(query.size().intValue());
		args.add((int) ((query.current() - 1) * query.size()));
		List<SummaryReportViews.Row> rows = jdbcTemplate.query(pageSql.toString(), this::mapSummaryRow,
				args.toArray());
		return new SummaryReportViews.Page(List.copyOf(rows), query.current(), query.size(),
				total == null ? 0 : total);
	}

	public String summaryCsv(SummaryReportQuery input) {
		SummaryReportQuery query = normalizeSummary(input);
		SummarySql sql = summarySql(query);
		List<SummaryReportViews.Row> rows = jdbcTemplate.query(sql.selectSql(), this::mapSummaryRow,
				sql.args().toArray());
		if (rows.isEmpty()) {
			return "";
		}
		return OcpxCsv.content(SUMMARY_CSV_HEADER, rows.stream().map(this::summaryCsvRow).toList());
	}

	private SummarySql summarySql(SummaryReportQuery query) {
		String period = switch (query.dateType()) {
		case 0 -> "r.time_hour";
		case 1 -> "LEFT(r.time_hour,8)";
		case 2 -> "LEFT(r.time_hour,6)";
		default -> "LEFT(r.time_hour,4)";
		};
		StringBuilder stat = new StringBuilder("SELECT ")
			.append(period)
			.append(" AS reportTime,r.m_id,r.a_id,r.t_id,r.l_id")
			.append(",IFNULL(SUM(r.uni_show_smt),0) AS uni_show_smt")
			.append(",IFNULL(SUM(r.uni_clk_smt),0) AS uni_clk_smt");
		for (String action : SUMMARY_ACTIONS) {
			stat.append(",IFNULL(SUM(r.").append(action).append("),0) AS ").append(action);
		}
		stat.append(",IFNULL(ROUND(SUM(r.base_price)/10000000,2),0) AS base_price")
			.append(",IFNULL(ROUND(SUM(r.uni_clk_smt)/SUM(r.uni_show_smt)*100,2),0) AS clickRate")
			.append(" FROM scx_ocpx_report_mat r WHERE r.time_hour BETWEEN ? AND ?");
		List<Object> args = new ArrayList<>();
		args.add(Integer.valueOf(query.startTime() + "00"));
		args.add(Integer.valueOf(query.endTime() + "23"));
		filter(stat, args, "r.m_id", query.mediaId());
		filter(stat, args, "r.t_id", query.taskId());
		filter(stat, args, "r.a_id", query.advertiserId());
		filter(stat, args, "r.l_id", query.promotionLinkId());
		stat.append(" GROUP BY r.m_id,r.a_id,r.t_id,r.l_id,").append(period);
		String select = "SELECT m.media_name,a.advertiser_name,p.link_name,t.task_name,dr.ratio AS link_radio,stat.* FROM ("
				+ stat + ") AS stat" + " JOIN scx_ocpx_media m ON m.id=stat.m_id"
				+ " JOIN scx_ocpx_advertiser a ON a.id=stat.a_id"
				+ " JOIN scx_ocpx_promotion_link p ON p.id=stat.l_id"
				+ " JOIN scx_ocpx_monitor_task t ON t.id=stat.t_id"
				+ " LEFT JOIN scx_ocpx_deduction_rule dr ON dr.media_id=stat.m_id AND dr.promotion_link_id=stat.l_id AND dr.deleted=0";
		return new SummarySql(stat.toString(), select, args);
	}

	private void appendSummaryOrder(StringBuilder sql, SummaryReportQuery query) {
		if (StringUtils.hasText(query.asc()) && SUMMARY_SORT_COLUMNS.containsKey(query.asc())) {
			sql.append(" ORDER BY ").append(SUMMARY_SORT_COLUMNS.get(query.asc())).append(" ASC");
		}
		else if (StringUtils.hasText(query.desc()) && SUMMARY_SORT_COLUMNS.containsKey(query.desc())) {
			sql.append(" ORDER BY ").append(SUMMARY_SORT_COLUMNS.get(query.desc())).append(" DESC");
		}
		else {
			sql.append(" ORDER BY reportTime DESC");
		}
	}

	private SummaryReportViews.Row mapSummaryRow(ResultSet rs, int rowNum) throws SQLException {
		Object ratio = rs.getObject("link_radio");
		return new SummaryReportViews.Row(rs.getString("reportTime"), rs.getString("m_id"), rs.getString("media_name"),
				rs.getString("t_id"), rs.getString("task_name"), rs.getString("a_id"), rs.getString("advertiser_name"),
				rs.getString("l_id"), rs.getString("link_name"), ratio == null ? null : ratio.toString(),
				rs.getLong("uni_show_smt"), rs.getLong("uni_clk_smt"), rs.getDouble("clickRate"),
				rs.getBigDecimal("base_price"), rs.getLong("activate"), rs.getLong("evoke"), rs.getLong("login"),
				rs.getLong("register"), rs.getLong("payment"), rs.getLong("customer_acquisition"),
				rs.getLong("next_stay"), rs.getLong("visits"), rs.getLong("authentication"),
				rs.getLong("place_order"), rs.getLong("finish_the_order"));
	}

	private List<Object> summaryCsvRow(SummaryReportViews.Row row) {
		return Arrays.asList(row.reportTime(), row.mediaName(), row.taskName(), row.advertiserName(),
				row.promotionLinkName(), row.linkRadio(), row.uniShowSmt(), row.uniClkSmt(), row.clickRate(),
				row.basePrice(), row.activate(), row.evoke(), row.login(), row.register(), row.payment(),
				row.customerAcquisition(), row.nextStay(), row.visits(), row.authentication(), row.placeOrder(),
				row.finishTheOrder());
	}

	private SummaryReportQuery normalizeSummary(SummaryReportQuery input) {
		SummaryReportQuery query = input == null
				? new SummaryReportQuery(null, null, null, null, null, null, null, null, null, null, null) : input;
		int dateType = query.dateType() == null ? 1 : query.dateType();
		if (dateType < 0 || dateType > 3) {
			throw new IllegalArgumentException("dateType 仅支持 0/1/2/3");
		}
		if (!isDayKey(query.startTime()) || !isDayKey(query.endTime())
				|| query.startTime().compareTo(query.endTime()) > 0) {
			throw new IllegalArgumentException("报表时间范围无效");
		}
		return new SummaryReportQuery(dateType, query.mediaId(), query.taskId(), query.advertiserId(),
				query.promotionLinkId(), query.startTime(), query.endTime(), query.asc(), query.desc(),
				query.current() == null ? 1 : Math.max(1, query.current()),
				query.size() == null ? 20 : Math.min(100, Math.max(1, query.size())));
	}

	private boolean isDayKey(String value) {
		return StringUtils.hasText(value) && value.matches("\\d{8}");
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
		filter(sql, args, "r.a_id", query.advertiserId());
		filter(sql, args, "r.l_id", query.promotionLinkId());
		filter(sql, args, "r.m_id", query.mediaId());
		filter(sql, args, "r.t_id", query.monitorTaskId());
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

	private record SummarySql(String statSql, String selectSql, List<Object> args) {
	}

	private record MetricRow(String key, String name, String metricCode, long count, BigDecimal amount) {
	}

	private record Dimension(String keySql, String nameSql, String joins) {

		static Dimension of(String groupBy) {
			return switch (groupBy) {
			case "ADVERTISER" ->
					new Dimension("r.a_id", "a.advertiser_name", "JOIN scx_ocpx_advertiser a ON a.id=r.a_id");
			case "PROMOTION_LINK" ->
					new Dimension("r.l_id", "p.link_name", "JOIN scx_ocpx_promotion_link p ON p.id=r.l_id");
			case "MEDIA" ->
					new Dimension("r.m_id", "m.media_name", "JOIN scx_ocpx_media m ON m.id=r.m_id");
			case "MONITOR_TASK" ->
					new Dimension("r.t_id", "t.task_name", "JOIN scx_ocpx_monitor_task t ON t.id=r.t_id");
				default -> new Dimension("r.time_hour",
						"DATE_FORMAT(STR_TO_DATE(CAST(r.time_hour AS CHAR),'%Y%m%d%H'),'%Y-%m-%d %H:00')", "");
			};
		}

	}

}
