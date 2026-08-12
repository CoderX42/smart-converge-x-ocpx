package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports are calculated directly from the retained Feedback/Callback flow log. */
@Service
@RequiredArgsConstructor
public class OcpxUnifiedReportService {

	private static final Set<String> GROUPS = Set.of("TIME", "ADVERTISER", "PROMOTION_LINK", "MEDIA",
			"MONITOR_TASK");

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
		StringBuilder sql = new StringBuilder("SELECT ").append(dimension.keySql()).append(",")
			.append(dimension.nameSql())
			.append(",CONCAT(f.flow_type,'_',f.result_status),COUNT(*) ")
			.append("FROM scx_ocpx_flow_log f ").append(dimension.joins())
			.append(" WHERE f.event_time>=? AND f.event_time<?");
		List<Object> args = new ArrayList<>();
		args.add(query.startDate().atStartOfDay());
		args.add(query.endDate().plusDays(1).atStartOfDay());
		filter(sql, args, "f.advertiser_id", query.advertiserId());
		filter(sql, args, "f.promotion_link_id", query.promotionLinkId());
		filter(sql, args, "f.media_id", query.mediaId());
		filter(sql, args, "f.monitor_task_id", query.monitorTaskId());
		sql.append(" GROUP BY ").append(dimension.keySql()).append(",").append(dimension.nameSql())
			.append(",f.flow_type,f.result_status ORDER BY ");
		if (ranking) {
			sql.append("COUNT(*) DESC,");
		}
		sql.append(dimension.keySql());
		return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new MetricRow(rs.getString(1), rs.getString(2),
				rs.getString(3), rs.getLong(4)), args.toArray());
	}

	private Map<String, UnifiedReportViews.Record> group(List<MetricRow> rows, String groupBy) {
		Map<String, Map<String, UnifiedReportViews.MetricValue>> metrics = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			metrics.computeIfAbsent(row.key(), ignored -> new LinkedHashMap<>())
				.put(row.metricCode(), new UnifiedReportViews.MetricValue(row.count(), BigDecimal.ZERO));
			names.putIfAbsent(row.key(), row.name());
		}
		Map<String, UnifiedReportViews.Record> result = new LinkedHashMap<>();
		metrics.forEach((key, value) -> result.put(key,
				new UnifiedReportViews.Record(key, groupBy, names.get(key), Map.copyOf(value))));
		return result;
	}

	private Map<String, UnifiedReportViews.MetricValue> summarize(List<MetricRow> rows) {
		Map<String, Long> totals = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			totals.merge(row.metricCode(), row.count(), Long::sum);
		}
		Map<String, UnifiedReportViews.MetricValue> result = new LinkedHashMap<>();
		totals.forEach((code, count) -> result.put(code, new UnifiedReportViews.MetricValue(count, BigDecimal.ZERO)));
		return Map.copyOf(result);
	}

	private List<UnifiedReportViews.Option> options(String table, String code, String name) {
		String sql = "SELECT id," + code + "," + name + ",status FROM " + table
				+ " WHERE deleted=0 ORDER BY " + name;
		return jdbcTemplate.query(sql, (rs, rowNum) -> new UnifiedReportViews.Option(rs.getString(1), rs.getString(2),
				rs.getString(3), rs.getString(4)));
	}

	private void filter(StringBuilder sql, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(" AND ").append(column).append("=?");
			args.add(value);
		}
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

	private record MetricRow(String key, String name, String metricCode, long count) {
	}

	private record Dimension(String keySql, String nameSql, String joins) {
		static Dimension of(String groupBy) {
			return switch (groupBy) {
				case "ADVERTISER" -> new Dimension("f.advertiser_id", "a.advertiser_name",
						"JOIN scx_ocpx_advertiser a ON a.id=f.advertiser_id");
				case "PROMOTION_LINK" -> new Dimension("f.promotion_link_id", "p.link_name",
						"JOIN scx_ocpx_promotion_link p ON p.id=f.promotion_link_id");
				case "MEDIA" -> new Dimension("f.media_id", "m.media_name",
						"JOIN scx_ocpx_media m ON m.id=f.media_id");
				case "MONITOR_TASK" -> new Dimension("f.monitor_task_id", "t.task_name",
						"JOIN scx_ocpx_monitor_task t ON t.id=f.monitor_task_id");
				default -> new Dimension("DATE_FORMAT(f.event_time,'%Y-%m-%d %H:00:00')",
						"DATE_FORMAT(f.event_time,'%Y-%m-%d %H:00')", "");
			};
		}
	}

}
