package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportViews;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.util.OcpxCsv;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class OcpxUnifiedReportService {

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final Set<String> GROUPS = Set.of("TIME", "ADVERTISER", "PROMOTION_LINK", "MEDIA", "MONITOR_TASK");

	private static final List<MetricColumn> METRICS = List.of(new MetricColumn("SHOW", "impression_count"),
			new MetricColumn("UNIQUE_SHOW", "impression_count"), new MetricColumn("CLICK", "click_count"),
			new MetricColumn("UNIQUE_CLICK", "click_count"),
			new MetricColumn("TARGET_CONVERSION", "target_conversion_count"),
			new MetricColumn("CALLBACK", "callback_count"),
			new MetricColumn("DEDUCTION_PASSED", "deduction_passed_count"),
			new MetricColumn("DEDUCTION_DEDUCTED", "deduction_deducted_count"),
			new MetricColumn("ACTIVATE", "activate_count"), new MetricColumn("EVOKE", "evoke_count"),
			new MetricColumn("LOGIN", "login_count"), new MetricColumn("REGISTER", "register_count"),
			new MetricColumn("PAYMENT", "payment_count"),
			new MetricColumn("CUSTOMER_ACQUISITION", "customer_acquisition_count"),
			new MetricColumn("NEXT_STAY", "next_stay_count"), new MetricColumn("VISITS", "visits_count"),
			new MetricColumn("AUTHENTICATION", "authentication_count"),
			new MetricColumn("PLACE_ORDER", "place_order_count"),
			new MetricColumn("FINISH_THE_ORDER", "finish_the_order_count"),
			new MetricColumn("FIRST_ACTIVATION", "first_activation_count"),
			new MetricColumn("FIRST_ORDER", "first_order_count"),
			new MetricColumn("DOWNLOAD", "download_count"), new MetricColumn("ACTIVITY", "activity_count"),
			new MetricColumn("ADDICTION", "addiction_count"),
			new MetricColumn("REJECTED_CLICK", "rejected_click_count"));

	private static final List<String> SUMMARY_ACTIONS = List.of("activate", "evoke", "login", "register", "payment",
			"customer_acquisition", "next_stay", "visits", "authentication", "place_order", "finish_the_order",
			"first_activation", "first_order", "download", "activity", "addiction");

	private static final List<String> SUMMARY_CSV_HEADER = List.of("报告时间", "媒体名称", "监测名称", "广告主名称", "链接名称", "下游配置放行率",
			"上游Callback数", "下游回传提交数", "SCX扣量拦截数", "SCX待判定数", "扣量统计起始时间", "去重展示数", "去重点击数", "点击率", "广告主消费", "上游激活数",
			"上游唤起数", "上游登录数", "上游注册数", "上游付费数", "上游获客数", "上游次留数", "上游访问数", "上游认证数", "上游下单数", "上游完单数", "上游首活数", "上游首购数", "上游下载数", "上游活动数", "上游深度使用数",
			"订单金额");

	private static final Map<String, String> SUMMARY_SORT_COLUMNS = Map.ofEntries(
			Map.entry("report_time", "reportTime"), Map.entry("uni_show_smt", "uni_show_smt"),
			Map.entry("uni_clk_smt", "uni_clk_smt"), Map.entry("click_rate", "clickRate"),
			Map.entry("callback_count", "callback_count"),
			Map.entry("deduction_passed_count", "deduction_passed_count"),
			Map.entry("deduction_deducted_count", "deduction_deducted_count"), Map.entry("base_price", "base_price"),
			Map.entry("activate", "activate"), Map.entry("evoke", "evoke"), Map.entry("login", "login"),
			Map.entry("register", "register"), Map.entry("payment", "payment"),
			Map.entry("customer_acquisition", "customer_acquisition"), Map.entry("next_stay", "next_stay"),
			Map.entry("visits", "visits"), Map.entry("authentication", "authentication"),
			Map.entry("place_order", "place_order"), Map.entry("finish_the_order", "finish_the_order"),
			Map.entry("first_activation", "first_activation"), Map.entry("first_order", "first_order"),
			Map.entry("download", "download"), Map.entry("activity", "activity"),
			Map.entry("addiction", "addiction"),
			Map.entry("currency_amount", "currency_amount"));

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	private final OcpxAdvertiserAccountService advertiserAccountService;

	public UnifiedReportViews.Page query(UnifiedReportQuery input) {
		UnifiedReportQuery query = normalize(input);
		validateFilterOwnership(query);
		List<MetricRow> rows = aggregate(query);
		Map<String, UnifiedReportViews.Row> grouped = group(rows, query.getGroupBy());
		List<UnifiedReportViews.Row> all = new ArrayList<>(grouped.values());
		long start = Math.min(all.size(), (query.getCurrent() - 1) * query.getSize());
		long end = Math.min(all.size(), start + query.getSize());
		return new UnifiedReportViews.Page(List.copyOf(all.subList((int) start, (int) end)), query.getCurrent(),
				query.getSize(), all.size(), summarize(rows), LocalDateTime.now());
	}

	public SummaryReportViews.Page summary(SummaryReportQuery input) {
		SummaryReportQuery query = normalizeSummary(input);
		validateFilterOwnership(summaryFilter(query));
		SummarySql sql = summarySql(query);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + sql.getStatSql() + ") AS stat", Long.class,
				sql.getArgs().toArray());
		StringBuilder pageSql = new StringBuilder(sql.getSelectSql());
		appendSummaryOrder(pageSql, query);
		pageSql.append(" LIMIT ? OFFSET ?");
		List<Object> args = new ArrayList<>(sql.getArgs());
		args.add(query.getSize().intValue());
		args.add((int) ((query.getCurrent() - 1) * query.getSize()));
		List<SummaryReportViews.Row> rows = jdbcTemplate.query(pageSql.toString(), this::mapSummaryRow, args.toArray());
		LocalDateTime coverageStart = deductionCoverageStart(query);
		return new SummaryReportViews.Page(List.copyOf(rows), query.getCurrent(), query.getSize(),
				total == null ? 0 : total, coverageStart, deductionCoverageStatus(query, coverageStart));
	}

	public String summaryCsv(SummaryReportQuery input) {
		SummaryReportQuery query = normalizeSummary(input);
		validateFilterOwnership(summaryFilter(query));
		SummarySql sql = summarySql(query);
		List<SummaryReportViews.Row> rows = jdbcTemplate.query(sql.getSelectSql(), this::mapSummaryRow,
				sql.getArgs().toArray());
		if (rows.isEmpty()) {
			return "";
		}
		LocalDateTime coverageStart = deductionCoverageStart(query);
		return OcpxCsv.content(SUMMARY_CSV_HEADER,
				rows.stream().map(row -> summaryCsvRow(row, coverageStart)).toList());
	}

	private SummarySql summarySql(SummaryReportQuery query) {
		String period = switch (query.getDateType()) {
			case 0 -> "r.time_hour";
			case 1 -> "LEFT(r.time_hour,8)";
			case 2 -> "LEFT(r.time_hour,6)";
			default -> "LEFT(r.time_hour,4)";
		};
		StringBuilder stat = new StringBuilder("SELECT ").append(period)
			.append(" AS reportTime,r.media_id AS m_id,r.advertiser_id AS a_id,")
			.append("r.monitor_task_id AS t_id,r.promotion_link_id AS l_id")
			.append(",IFNULL(SUM(r.impression_count),0) AS uni_show_smt")
			.append(",IFNULL(SUM(r.click_count),0) AS uni_clk_smt");
		for (String action : SUMMARY_ACTIONS) {
			stat.append(",IFNULL(SUM(r.").append(action).append("_count),0) AS ").append(action);
		}
		stat.append(",IFNULL(SUM(r.currency_amount),0) AS currency_amount");
		stat.append(",IFNULL(SUM(r.callback_count),0) AS callback_count")
			.append(",IFNULL(SUM(r.deduction_passed_count),0) AS deduction_passed_count")
			.append(",IFNULL(SUM(r.deduction_deducted_count),0) AS deduction_deducted_count");
		stat.append(",IFNULL(SUM(r.spend_amount),0) AS base_price")
			.append(",CASE WHEN SUM(r.impression_count)=0 THEN NULL ")
			.append("ELSE ROUND(SUM(r.click_count)/SUM(r.impression_count)*100,6) END AS clickRate")
			.append(" FROM scx_ocpx_hourly_metric r WHERE r.time_hour BETWEEN ? AND ?");
		List<Object> args = new ArrayList<>();
		args.add(Integer.valueOf(query.getStartTime() + "00"));
		args.add(Integer.valueOf(query.getEndTime() + "23"));
		filter(stat, args, "r.media_id", query.getMediaId());
		filter(stat, args, "r.monitor_task_id", query.getMonitorTaskId());
		filter(stat, args, "r.advertiser_id", query.getAdvertiserId());
		filter(stat, args, "r.promotion_link_id", query.getPromotionLinkId());
		stat.append(" GROUP BY r.media_id,r.advertiser_id,r.monitor_task_id,r.promotion_link_id,").append(period);
		String select = "SELECT m.media_name,a.advertiser_name,p.link_name,t.monitor_name,dr.ratio AS link_radio,stat.* FROM ("
				+ stat + ") AS stat" + " JOIN scx_ocpx_media m ON m.id=stat.m_id"
				+ " JOIN scx_ocpx_advertiser a ON a.id=stat.a_id" + " JOIN scx_ocpx_promotion_link p ON p.id=stat.l_id"
				+ " JOIN scx_ocpx_monitor_task t ON t.id=stat.t_id"
				+ " LEFT JOIN scx_ocpx_deduction_rule dr ON dr.media_id=stat.m_id AND dr.promotion_link_id=stat.l_id AND dr.is_delete=0";
		return new SummarySql(stat.toString(), select, args);
	}

	private void appendSummaryOrder(StringBuilder sql, SummaryReportQuery query) {
		if (StringUtils.hasText(query.getAsc()) && SUMMARY_SORT_COLUMNS.containsKey(query.getAsc())) {
			sql.append(" ORDER BY ").append(SUMMARY_SORT_COLUMNS.get(query.getAsc())).append(" ASC");
		}
		else if (StringUtils.hasText(query.getDesc()) && SUMMARY_SORT_COLUMNS.containsKey(query.getDesc())) {
			sql.append(" ORDER BY ").append(SUMMARY_SORT_COLUMNS.get(query.getDesc())).append(" DESC");
		}
		else {
			sql.append(" ORDER BY reportTime DESC");
		}
	}

	private SummaryReportViews.Row mapSummaryRow(ResultSet rs, int rowNum) throws SQLException {
		Object ratio = rs.getObject("link_radio");
		return new SummaryReportViews.Row(rs.getString("reportTime"), rs.getString("m_id"), rs.getString("media_name"),
				rs.getString("t_id"), rs.getString("monitor_name"), rs.getString("a_id"),
				rs.getString("advertiser_name"), rs.getString("l_id"), rs.getString("link_name"),
				ratio == null ? null : ratio.toString(), rs.getLong("callback_count"),
				rs.getLong("deduction_passed_count"), rs.getLong("deduction_deducted_count"),
				Math.max(0,
						rs.getLong("callback_count") - rs.getLong("deduction_passed_count")
								- rs.getLong("deduction_deducted_count")),
				rs.getLong("uni_show_smt"), rs.getLong("uni_clk_smt"),
				rs.getObject("clickRate") == null ? null : rs.getDouble("clickRate"), rs.getBigDecimal("base_price"),
				rs.getLong("activate"), rs.getLong("evoke"), rs.getLong("login"), rs.getLong("register"),
				rs.getLong("payment"), rs.getLong("customer_acquisition"), rs.getLong("next_stay"),
				rs.getLong("visits"), rs.getLong("authentication"), rs.getLong("place_order"),
				rs.getLong("finish_the_order"), rs.getLong("first_activation"), rs.getLong("first_order"),
				rs.getLong("download"), rs.getLong("activity"), rs.getLong("addiction"),
				rs.getBigDecimal("currency_amount"));
	}

	private List<Object> summaryCsvRow(SummaryReportViews.Row row, LocalDateTime coverageStart) {
		return Arrays.asList(row.getReportTime(), row.getMediaName(), row.getMonitorName(), row.getAdvertiserName(),
				row.getPromotionLinkName(), mediaCallbackRatio(row.getLinkRadio()), row.getCallbackCount(),
				row.getDeductionPassedCount(), row.getDeductionDeductedCount(), row.getDeductionPendingCount(),
				coverageStart == null ? "" : coverageStart, row.getUniShowSmt(), row.getUniClkSmt(), row.getClickRate(),
				row.getBasePrice(), row.getActivate(), row.getEvoke(), row.getLogin(), row.getRegister(),
				row.getPayment(), row.getCustomerAcquisition(), row.getNextStay(), row.getVisits(),
				row.getAuthentication(), row.getPlaceOrder(), row.getFinishTheOrder(), row.getFirstActivation(),
				row.getFirstOrder(), row.getDownload(), row.getActivity(), row.getAddiction(), row.getCurrencyAmount());
	}

	private String mediaCallbackRatio(String ratio) {
		if (!StringUtils.hasText(ratio)) {
			return "100%";
		}
		return new BigDecimal(ratio).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
	}

	private LocalDateTime deductionCoverageStart(SummaryReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				SELECT MIN(deduction_decided_at)
				FROM scx_ocpx_event_fact
				WHERE flow_type='CALLBACK' AND deduction_decision IN ('PASSED','DEDUCTED')
				""");
		List<Object> args = new ArrayList<>();
		filter(sql, args, "media_id", query.getMediaId());
		filter(sql, args, "monitor_task_id", query.getMonitorTaskId());
		filter(sql, args, "advertiser_id", query.getAdvertiserId());
		filter(sql, args, "promotion_link_id", query.getPromotionLinkId());
		return jdbcTemplate.queryForObject(sql.toString(), LocalDateTime.class, args.toArray());
	}

	private String deductionCoverageStatus(SummaryReportQuery query, LocalDateTime coverageStart) {
		if (coverageStart == null) {
			return "NONE";
		}
		LocalDateTime start = LocalDate.parse(query.getStartTime(), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
		LocalDateTime end = LocalDate.parse(query.getEndTime(), DateTimeFormatter.BASIC_ISO_DATE)
			.plusDays(1)
			.atStartOfDay();
		if (!end.isAfter(coverageStart)) {
			return "NONE";
		}
		return start.isBefore(coverageStart) ? "PARTIAL" : "FULL";
	}

	private SummaryReportQuery normalizeSummary(SummaryReportQuery input) {
		SummaryReportQuery query = input == null
				? new SummaryReportQuery(null, null, null, null, null, null, null, null, null, null, null) : input;
		int dateType = query.getDateType() == null ? 1 : query.getDateType();
		if (dateType < 0 || dateType > 3) {
			throw new BootBusinessException("dateType 仅支持 0/1/2/3");
		}
		if (!isDayKey(query.getStartTime()) || !isDayKey(query.getEndTime())
				|| query.getStartTime().compareTo(query.getEndTime()) > 0) {
			throw new BootBusinessException("报表时间范围无效");
		}
		return new SummaryReportQuery(dateType, query.getMediaId(), query.getMonitorTaskId(),
				advertiserScope.constrain(query.getAdvertiserId()), query.getPromotionLinkId(), query.getStartTime(),
				query.getEndTime(), query.getAsc(), query.getDesc(),
				query.getCurrent() == null ? 1 : Math.max(1, query.getCurrent()),
				query.getSize() == null ? 30 : Math.min(100, Math.max(1, query.getSize())));
	}

	private boolean isDayKey(String value) {
		return StringUtils.hasText(value) && value.matches("\\d{8}");
	}

	public UnifiedReportViews.Dashboard dashboard(UnifiedReportQuery input) {
		UnifiedReportQuery query = normalizeDashboard(input);
		validateFilterOwnership(query);
		List<MetricRow> rows = aggregate(query);
		Map<String, UnifiedReportViews.MetricValue> summary = summarize(rows);
		LocalDateTime asOf = aggregateWatermark(query);
		String accountAdvertiserId = advertiserScope.accountAdvertiserId(query.getAdvertiserId());
		UnifiedReportViews.Account account = StringUtils.hasText(accountAdvertiserId)
				? account(accountAdvertiserId, LocalDate.now(BUSINESS_ZONE)) : null;
		return new UnifiedReportViews.Dashboard(LocalDate.now(BUSINESS_ZONE), BUSINESS_ZONE.getId(),
				query.getStartDate(), query.getEndDate(), asOf, asOf == null ? "EMPTY" : "READY", account,
				dashboardMetrics(summary));
	}

	public UnifiedReportViews.Options options() {
		String scopedAdvertiserId = advertiserScope.currentAdvertiserId();
		List<UnifiedReportViews.Option> advertisers = advertiserOptions(scopedAdvertiserId);
		if (StringUtils.hasText(scopedAdvertiserId)) {
			return new UnifiedReportViews.Options(advertisers, options(
					"SELECT id,link_name FROM scx_ocpx_promotion_link WHERE is_delete=0 AND advertiser_id=? ORDER BY link_name",
					scopedAdvertiserId),
					options("SELECT DISTINCT m.id,m.media_name FROM scx_ocpx_media m JOIN scx_ocpx_monitor_task t ON t.media_id=m.id AND t.is_delete=0 WHERE t.advertiser_id=? ORDER BY m.media_name",
							scopedAdvertiserId),
					options("SELECT id,monitor_name FROM scx_ocpx_monitor_task WHERE is_delete=0 AND advertiser_id=? ORDER BY monitor_name",
							scopedAdvertiserId));
		}
		return new UnifiedReportViews.Options(advertisers,
				options("scx_ocpx_promotion_link", "link_name", "is_delete=0"),
				options("scx_ocpx_media", "media_name", null),
				options("scx_ocpx_monitor_task", "monitor_name", "is_delete=0"));
	}

	private List<UnifiedReportViews.Option> advertiserOptions(String scopedAdvertiserId) {
		List<UnifiedReportViews.Option> advertisers = StringUtils.hasText(scopedAdvertiserId)
				? options("SELECT id,advertiser_name FROM scx_ocpx_advertiser WHERE id=? ORDER BY advertiser_name",
						scopedAdvertiserId)
				: options("scx_ocpx_advertiser", "advertiser_name", null);
		return advertisers;
	}

	private Map<String, UnifiedReportViews.DashboardMetric> dashboardMetrics(
			Map<String, UnifiedReportViews.MetricValue> summary) {
		BigDecimal spend = amount(summary, "BASE_PRICE");
		BigDecimal impressions = count(summary, "UNIQUE_SHOW");
		BigDecimal clicks = count(summary, "UNIQUE_CLICK");
		BigDecimal conversions = count(summary, "TARGET_CONVERSION");
		Map<String, UnifiedReportViews.DashboardMetric> metrics = new LinkedHashMap<>();
		metrics.put("SPEND", metric(spend, "CNY"));
		metrics.put("IMPRESSION", metric(impressions, "COUNT"));
		metrics.put("CLICK", metric(clicks, "COUNT"));
		metrics.put("CONVERSION", metric(conversions, "COUNT"));
		metrics.put("CTR", ratio(clicks, impressions, BigDecimal.valueOf(100), "PERCENT"));
		metrics.put("CVR", ratio(conversions, clicks, BigDecimal.valueOf(100), "PERCENT"));
		metrics.put("CPM", ratio(spend, impressions, BigDecimal.valueOf(1000), "CNY"));
		metrics.put("CPC", ratio(spend, clicks, BigDecimal.ONE, "CNY"));
		metrics.put("CPA", ratio(spend, conversions, BigDecimal.ONE, "CNY"));
		return Map.copyOf(metrics);
	}

	private UnifiedReportViews.Account account(String advertiserId, LocalDate businessDate) {
		int startHour = hourKey(businessDate, "00");
		int endHour = hourKey(businessDate, "23");
		AccountActivity activity = jdbcTemplate.queryForObject("""
				SELECT IFNULL(SUM(spend_amount),0),IFNULL(SUM(rejected_click_count),0),
				       IFNULL(SUM(rejected_conversion_count),0)
				FROM scx_ocpx_hourly_metric
				WHERE advertiser_id=? AND time_hour BETWEEN ? AND ?
				""", (rs, rowNum) -> new AccountActivity(rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3)),
				advertiserId, startHour, endHour);
		return new UnifiedReportViews.Account(advertiserId, advertiserAccountService.balance(advertiserId),
				activity == null ? BigDecimal.ZERO.setScale(6) : activity.todaySpend(),
				activity == null ? 0 : activity.rejectedClickCount(),
				activity == null ? 0 : activity.rejectedConversionCount());
	}

	private LocalDateTime aggregateWatermark(UnifiedReportQuery query) {
		StringBuilder sql = new StringBuilder(
				"SELECT MAX(update_time) FROM scx_ocpx_hourly_metric WHERE time_hour BETWEEN ? AND ?");
		List<Object> args = new ArrayList<>();
		args.add(hourKey(query.getStartDate(), "00"));
		args.add(hourKey(query.getEndDate(), "23"));
		filter(sql, args, "advertiser_id", query.getAdvertiserId());
		filter(sql, args, "promotion_link_id", query.getPromotionLinkId());
		filter(sql, args, "media_id", query.getMediaId());
		filter(sql, args, "monitor_task_id", query.getMonitorTaskId());
		return jdbcTemplate.queryForObject(sql.toString(), LocalDateTime.class, args.toArray());
	}

	private void validateFilterOwnership(UnifiedReportQuery query) {
		if (StringUtils.hasText(query.getAdvertiserId())) {
			requireExists("SELECT COUNT(*) FROM scx_ocpx_advertiser WHERE id=?", "广告主不存在", query.getAdvertiserId());
		}
		if (StringUtils.hasText(query.getPromotionLinkId())) {
			StringBuilder linkSql = new StringBuilder(
					"SELECT COUNT(*) FROM scx_ocpx_promotion_link WHERE id=? AND is_delete=0");
			List<Object> args = new ArrayList<>();
			args.add(query.getPromotionLinkId());
			filter(linkSql, args, "advertiser_id", query.getAdvertiserId());
			requireExists(linkSql.toString(), "推广链接与广告主不匹配", args.toArray());
		}
		if (StringUtils.hasText(query.getMediaId())) {
			requireExists("SELECT COUNT(*) FROM scx_ocpx_media WHERE id=?", "媒体不存在", query.getMediaId());
		}
		if (StringUtils.hasText(query.getMonitorTaskId())) {
			StringBuilder taskSql = new StringBuilder(
					"SELECT COUNT(*) FROM scx_ocpx_monitor_task WHERE id=? AND is_delete=0");
			List<Object> args = new ArrayList<>();
			args.add(query.getMonitorTaskId());
			filter(taskSql, args, "advertiser_id", query.getAdvertiserId());
			filter(taskSql, args, "promotion_link_id", query.getPromotionLinkId());
			filter(taskSql, args, "media_id", query.getMediaId());
			requireExists(taskSql.toString(), "监测任务与筛选对象不匹配", args.toArray());
		}
		else if (specified(query.getAdvertiserId(), query.getPromotionLinkId(), query.getMediaId()) >= 2) {
			StringBuilder routeSql = new StringBuilder("SELECT COUNT(*) FROM scx_ocpx_monitor_task WHERE is_delete=0");
			List<Object> args = new ArrayList<>();
			filter(routeSql, args, "advertiser_id", query.getAdvertiserId());
			filter(routeSql, args, "promotion_link_id", query.getPromotionLinkId());
			filter(routeSql, args, "media_id", query.getMediaId());
			requireExists(routeSql.toString(), "筛选对象之间不存在有效监测任务关系", args.toArray());
		}
	}

	private void requireExists(String sql, String message, Object... args) {
		Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
		if (count == null || count.longValue() == 0) {
			throw new BootBusinessException(message);
		}
	}

	private int specified(String... values) {
		return (int) Arrays.stream(values).filter(StringUtils::hasText).count();
	}

	private UnifiedReportQuery summaryFilter(SummaryReportQuery query) {
		return new UnifiedReportQuery(LocalDate.parse(query.getStartTime(), DateTimeFormatter.BASIC_ISO_DATE),
				LocalDate.parse(query.getEndTime(), DateTimeFormatter.BASIC_ISO_DATE), "TIME", query.getAdvertiserId(),
				query.getPromotionLinkId(), query.getMediaId(), query.getMonitorTaskId(), 1, 1);
	}

	private UnifiedReportViews.DashboardMetric metric(BigDecimal value, String unit) {
		return new UnifiedReportViews.DashboardMetric(value, unit, "OK");
	}

	private UnifiedReportViews.DashboardMetric ratio(BigDecimal numerator, BigDecimal denominator,
			BigDecimal multiplier, String unit) {
		if (denominator.signum() == 0) {
			return new UnifiedReportViews.DashboardMetric(null, unit, "ZERO_DENOMINATOR");
		}
		return metric(numerator.multiply(multiplier).divide(denominator, 6, RoundingMode.HALF_UP), unit);
	}

	private BigDecimal count(Map<String, UnifiedReportViews.MetricValue> summary, String code) {
		UnifiedReportViews.MetricValue value = summary.get(code);
		return BigDecimal.valueOf(value == null ? 0 : value.getCount());
	}

	private BigDecimal amount(Map<String, UnifiedReportViews.MetricValue> summary, String code) {
		UnifiedReportViews.MetricValue value = summary.get(code);
		return value == null || value.getAmount() == null ? BigDecimal.ZERO.setScale(6) : value.getAmount();
	}

	private List<MetricRow> aggregate(UnifiedReportQuery query) {
		Dimension dimension = Dimension.of(query.getGroupBy());
		StringBuilder sql = new StringBuilder("SELECT ").append(dimension.getKeySql())
			.append(",")
			.append(dimension.getNameSql());
		for (MetricColumn metric : METRICS) {
			sql.append(",IFNULL(SUM(r.").append(metric.getColumn()).append("),0)");
		}
		sql.append(",IFNULL(SUM(r.spend_amount),0),IFNULL(SUM(r.currency_amount),0) FROM scx_ocpx_hourly_metric r ")
			.append(dimension.getJoins())
			.append(" WHERE r.time_hour>=? AND r.time_hour<=?");
		List<Object> args = new ArrayList<>();
		args.add(hourKey(query.getStartDate(), "00"));
		args.add(hourKey(query.getEndDate(), "23"));
		filter(sql, args, "r.advertiser_id", query.getAdvertiserId());
		filter(sql, args, "r.promotion_link_id", query.getPromotionLinkId());
		filter(sql, args, "r.media_id", query.getMediaId());
		filter(sql, args, "r.monitor_task_id", query.getMonitorTaskId());
		sql.append(" GROUP BY ").append(dimension.getKeySql()).append(",").append(dimension.getNameSql());
		return jdbcTemplate.query(sql.toString(), (ResultSetExtractor<List<MetricRow>>) this::readMetricRows,
				args.toArray());
	}

	private List<MetricRow> readMetricRows(ResultSet resultSet) throws SQLException {
		List<MetricRow> rows = new ArrayList<>();
		while (resultSet.next()) {
			String key = resultSet.getString(1);
			String name = resultSet.getString(2);
			for (int index = 0; index < METRICS.size(); index++) {
				rows.add(new MetricRow(key, name, METRICS.get(index).getCode(), resultSet.getLong(index + 3),
						BigDecimal.ZERO));
			}
			BigDecimal amount = resultSet.getBigDecimal(METRICS.size() + 3);
			rows.add(new MetricRow(key, name, "BASE_PRICE", 0, amount == null ? BigDecimal.ZERO.setScale(6) : amount));
			BigDecimal currencyAmount = resultSet.getBigDecimal(METRICS.size() + 4);
			rows.add(new MetricRow(key, name, "CURRENCY_AMOUNT", 0,
					currencyAmount == null ? BigDecimal.ZERO.setScale(6) : currencyAmount));
		}
		return rows;
	}

	private Map<String, UnifiedReportViews.Row> group(List<MetricRow> rows, String groupBy) {
		Map<String, Map<String, UnifiedReportViews.MetricValue>> metrics = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			metrics.computeIfAbsent(row.getKey(), ignored -> new LinkedHashMap<>())
				.put(row.getMetricCode(), new UnifiedReportViews.MetricValue(row.getCount(), row.getAmount()));
			names.putIfAbsent(row.getKey(), row.getName());
		}
		Map<String, UnifiedReportViews.Row> result = new LinkedHashMap<>();
		metrics.forEach((key, value) -> result.put(key,
				new UnifiedReportViews.Row(key, groupBy, names.get(key), Map.copyOf(value))));
		return result;
	}

	private Map<String, UnifiedReportViews.MetricValue> summarize(List<MetricRow> rows) {
		Map<String, Long> counts = new LinkedHashMap<>();
		Map<String, BigDecimal> amounts = new LinkedHashMap<>();
		for (MetricRow row : rows) {
			counts.merge(row.getMetricCode(), row.getCount(), Long::sum);
			amounts.merge(row.getMetricCode(), row.getAmount(), BigDecimal::add);
		}
		Map<String, UnifiedReportViews.MetricValue> result = new LinkedHashMap<>();
		counts.forEach((code, count) -> result.put(code, new UnifiedReportViews.MetricValue(count, amounts.get(code))));
		return Map.copyOf(result);
	}

	private List<UnifiedReportViews.Option> options(String table, String name, String predicate) {
		String sql = "SELECT id," + name + " FROM " + table + (predicate == null ? "" : " WHERE " + predicate)
				+ " ORDER BY " + name;
		return jdbcTemplate.query(sql,
				(resultSet, rowNum) -> new UnifiedReportViews.Option(resultSet.getString(1), resultSet.getString(2)));
	}

	private List<UnifiedReportViews.Option> options(String sql, String advertiserId) {
		return jdbcTemplate.query(sql,
				(resultSet, rowNum) -> new UnifiedReportViews.Option(resultSet.getString(1), resultSet.getString(2)),
				advertiserId);
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
		LocalDate end = query == null || query.getEndDate() == null ? LocalDate.now(BUSINESS_ZONE) : query.getEndDate();
		LocalDate start = query == null || query.getStartDate() == null ? end.minusDays(6) : query.getStartDate();
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new BootBusinessException("报表时间范围无效");
		}
		String groupBy = query == null || !StringUtils.hasText(query.getGroupBy()) ? "TIME"
				: query.getGroupBy().trim().toUpperCase();
		if (!GROUPS.contains(groupBy)) {
			throw new BootBusinessException("groupBy 仅支持 TIME/ADVERTISER/PROMOTION_LINK/MEDIA/MONITOR_TASK");
		}
		return new UnifiedReportQuery(start, end, groupBy,
				advertiserScope.constrain(query == null ? null : query.getAdvertiserId()),
				query == null ? null : query.getPromotionLinkId(), query == null ? null : query.getMediaId(),
				query == null ? null : query.getMonitorTaskId(), query == null ? 1 : Math.max(1, query.getCurrent()),
				query == null ? 30 : Math.min(100, Math.max(1, query.getSize())));
	}

	private UnifiedReportQuery normalizeDashboard(UnifiedReportQuery query) {
		LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
		LocalDate start = query == null ? null : query.getStartDate();
		LocalDate end = query == null ? null : query.getEndDate();
		if (start == null && end == null) {
			start = businessDate;
			end = businessDate;
		}
		else if (start == null) {
			start = end;
		}
		else if (end == null) {
			end = start;
		}
		return normalize(new UnifiedReportQuery(start, end, query == null ? "TIME" : query.getGroupBy(),
				query == null ? null : query.getAdvertiserId(), query == null ? null : query.getPromotionLinkId(),
				query == null ? null : query.getMediaId(), query == null ? null : query.getMonitorTaskId(),
				query == null ? 1 : query.getCurrent(), query == null ? 30 : query.getSize()));
	}

	@Getter
	@AllArgsConstructor
	private static final class MetricColumn {

		private final String code;

		private final String column;

	}

	@Getter
	@AllArgsConstructor
	private static final class SummarySql {

		private final String statSql;

		private final String selectSql;

		private final List<Object> args;

	}

	@Getter
	@AllArgsConstructor
	private static final class MetricRow {

		private final String key;

		private final String name;

		private final String metricCode;

		private final long count;

		private final BigDecimal amount;

	}

	@Getter
	@AllArgsConstructor
	private static final class Dimension {

		private final String keySql;

		private final String nameSql;

		private final String joins;

		static Dimension of(String groupBy) {
			return switch (groupBy) {
				case "ADVERTISER" -> new Dimension("r.advertiser_id", "a.advertiser_name",
						"JOIN scx_ocpx_advertiser a ON a.id=r.advertiser_id");
				case "PROMOTION_LINK" -> new Dimension("r.promotion_link_id", "p.link_name",
						"JOIN scx_ocpx_promotion_link p ON p.id=r.promotion_link_id");
				case "MEDIA" -> new Dimension("r.media_id", "m.media_name", "JOIN scx_ocpx_media m ON m.id=r.media_id");
				case "MONITOR_TASK" -> new Dimension("r.monitor_task_id", "t.monitor_name",
						"JOIN scx_ocpx_monitor_task t ON t.id=r.monitor_task_id");
				default -> new Dimension("r.time_hour",
						"DATE_FORMAT(STR_TO_DATE(CAST(r.time_hour AS CHAR),'%Y%m%d%H'),'%Y-%m-%d %H:00')", "");
			};
		}

	}

	private record AccountActivity(BigDecimal todaySpend, long rejectedClickCount, long rejectedConversionCount) {
	}

}
