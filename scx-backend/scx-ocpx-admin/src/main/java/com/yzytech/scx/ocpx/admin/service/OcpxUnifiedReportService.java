package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.config.OcpxReportingProperties;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.PermissionLevel;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Restriction;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.ScopeType;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxUnifiedReportService {

	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	private static final Set<String> GRAINS = Set.of("HOUR", "DAY");

	private static final Set<String> GROUPS = Set.of("TIME", "PRODUCT", "LINK", "TASK", "MEDIA");

	private static final Set<String> MEDIA_LEVELS = Set.of("ACCOUNT", "CHANNEL");

	private static final Set<String> DIRECTIONS = Set.of("ASC", "DESC");

	private static final Map<String, String> SORTS = Map.of("dimension", "dimensionKey", "impressions", "impressions",
			"rawClicks", "rawClicks", "validClicks", "validClicks", "activations", "activations", "registrations",
			"registrations", "payments", "payments", "costAmount", "costAmount");

	private static final Set<String> DEFAULT_METRICS = Set.of("impressions", "rawClicks", "validClicks",
			"duplicateClicks", "activations", "registrations", "payments", "activationRate", "registrationRate",
			"paymentRate");

	private final NamedParameterJdbcTemplate jdbcTemplate;

	private final OcpxAccessScope accessScope;

	private final OcpxReportingProperties properties;

	private final OcpxMetricDictionary metricDictionary;

	public UnifiedReportViews.Page query(UnifiedReportQuery requested) {
		boolean sensitive = StpUtil.hasPermission("ocpx:report:metric:sensitive");
		Validated query = validate(requested, sensitive, properties.getMaximumQueryDays());
		requireExplicitScope(query);
		return execute(query, freeze(accessScope.current()), sensitive);
	}

	public UnifiedReportViews.Options options() {
		return options(currentScope());
	}

	DashboardSession openDashboardSession(UnifiedReportQuery watermarkProbe) {
		boolean sensitive = StpUtil.hasPermission("ocpx:report:metric:sensitive");
		Validated query = validate(watermarkProbe, sensitive, properties.getMaximumQueryDays() * 2);
		OcpxAccessScope.Snapshot snapshot = accessScope.current();
		FrozenScope scope = freeze(snapshot);
		requireExplicitScope(query, scope);
		return new DashboardSession(scope, snapshot.version(), scopeHash(scope), watermark(query, scope), sensitive,
				query.start(), query.end());
	}

	UnifiedReportViews.Page queryForDashboard(UnifiedReportQuery requested, DashboardSession session) {
		Validated query = validate(requested, session.sensitive(), properties.getMaximumQueryDays());
		if (query.start().isBefore(session.minimumDate()) || query.end().isAfter(session.maximumDate())) {
			throw new IllegalArgumentException("Dashboard section exceeds its frozen query window");
		}
		requireExplicitScope(query, session.scope());
		return execute(query, session.scope(), session.sensitive());
	}

	UnifiedReportViews.Options optionsForDashboard() {
		return options(currentScope());
	}

	private UnifiedReportViews.Options options(FrozenScope scope) {
		return new UnifiedReportViews.Options(
				options("SELECT id,advertiser_name,status FROM scx_ocpx_advertiser WHERE deleted=0 AND status='ACTIVE'",
						"id", scope.advertisers()),
				options("SELECT id,name,status FROM scx_ocpx_product WHERE status='ACTIVE'", "advertiser_id",
						scope.advertisers()),
				options("SELECT id,link_name,status FROM scx_ocpx_app_link WHERE deleted=0 AND status='ACTIVE'",
						"advertiser_id", scope.advertisers()),
				optionsWithTaskScope(scope),
				options("SELECT id,channel_name,status FROM scx_ocpx_channel WHERE deleted=0 AND status='ACTIVE'", "id",
						scope.channels()),
				optionsWithMediaScope(scope));
	}

	UnifiedReportViews.Page queryForExport(UnifiedReportQuery requested, FrozenScope scope, boolean sensitive) {
		return execute(validate(requested, sensitive, properties.getMaximumExportDays()), scope, sensitive);
	}

	FrozenScope currentScope() {
		return freeze(accessScope.current());
	}

	FrozenScope scopeForUser(String userId) {
		return freeze(accessScope.forUser(userId));
	}

	private UnifiedReportViews.Page execute(Validated query, FrozenScope frozenScope, boolean sensitive) {
		ScopeClause scope = scope(frozenScope);
		MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("start", query.start().atStartOfDay())
			.addValue("end", query.end().plusDays(1).atStartOfDay())
			.addValue("offset", (query.current() - 1) * query.size())
			.addValue("size", query.size());
		StringBuilder where = new StringBuilder(" WHERE h.time_hour>=:start AND h.time_hour<:end");
		appendFilter(where, parameters, "h.advertiser_id", "advertiserId", query.advertiserId());
		appendFilter(where, parameters, "h.product_id", "productId", query.productId());
		appendFilter(where, parameters, "h.app_link_id", "appLinkId", query.appLinkId());
		appendFilter(where, parameters, "h.task_id", "taskId", query.taskId());
		appendFilter(where, parameters, "h.channel_id", "channelId", query.channelId());
		appendFilter(where, parameters, "h.media_account_id", "mediaAccountId", query.mediaAccountId());
		where.append(scope.sql());
		scope.parameters().forEach(parameters::addValue);

		String dimensionExpression = dimensionExpression(query);
		String grouped = groupedSql(dimensionExpression, where.toString());
		Long totalValue = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + grouped + ") grouped_count",
				parameters, Long.class);
		String sortExpression = SORTS.get(query.sort());
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(grouped + " ORDER BY " + sortExpression + " "
				+ query.direction() + ",dimensionKey ASC LIMIT :size OFFSET :offset", parameters);
		boolean dailyTaskOrLink = "DAY".equals(query.grain())
				&& ("TASK".equals(query.groupBy()) || "LINK".equals(query.groupBy()));
		List<UnifiedReportViews.Record> records = rows.stream()
			.map(row -> record(row, query, sensitive, dailyTaskOrLink))
			.toList();
		Map<String, BigDecimal> queryBase = base(jdbcTemplate.queryForMap(summarySql(where.toString()), parameters));
		Map<String, BigDecimal> pageBase = sumBase(rows);
		LocalDateTime asOf = timestamp(rows, "asOf");
		String freshness = freshness(rows, asOf);
		return new UnifiedReportViews.Page(records, query.current(), query.size(), totalValue == null ? 0 : totalValue,
				new UnifiedReportViews.Summary(
						metricDictionary.select(pageBase, query.metrics(), dailyTaskOrLink, sensitive), "当前页汇总"),
				new UnifiedReportViews.Summary(
						metricDictionary.select(queryBase, query.metrics(), dailyTaskOrLink, sensitive), "筛选范围全量汇总"),
				asOf, REPORT_ZONE.getId(), freshness, OcpxMetricDictionary.VERSION,
				metricDictionary.definitions(dailyTaskOrLink, sensitive));
	}

	private Validated validate(UnifiedReportQuery requested, boolean sensitive, int maximumDays) {
		LocalDate today = LocalDate.now(REPORT_ZONE);
		LocalDate end = requested.endDate() == null ? today : requested.endDate();
		LocalDate start = requested.startDate() == null ? end.minusDays(properties.getDefaultQueryDays() - 1L)
				: requested.startDate();
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能晚于结束日期");
		}
		if (start.plusDays(maximumDays - 1L).isBefore(end)) {
			throw new IllegalArgumentException("查询跨度超过允许上限");
		}
		String grain = normalized(requested.grain(), "DAY", GRAINS, "grain");
		String groupBy = normalized(requested.groupBy(), "TIME", GROUPS, "groupBy");
		String mediaLevel = normalized(requested.mediaLevel(), "ACCOUNT", MEDIA_LEVELS, "mediaLevel");
		String direction = normalized(requested.direction(), "DESC", DIRECTIONS, "direction");
		String sort = StringUtils.hasText(requested.sort()) ? requested.sort() : "dimension";
		if (!SORTS.containsKey(sort)) {
			throw new IllegalArgumentException("sort 不在白名单内");
		}
		long current = requested.current() <= 0 ? 1 : requested.current();
		long size = requested.size() <= 0 ? 20 : requested.size();
		if (size > properties.getMaximumPageSize()) {
			throw new IllegalArgumentException("pageSize 超过允许上限");
		}
		Set<String> metrics = requested.metrics() == null || requested.metrics().isEmpty() ? DEFAULT_METRICS
				: new LinkedHashSet<>(requested.metrics());
		if (!metricDictionary.allowed().containsAll(metrics)) {
			throw new IllegalArgumentException("metrics 包含未开放指标");
		}
		boolean dailyTaskOrLink = "DAY".equals(grain) && ("TASK".equals(groupBy) || "LINK".equals(groupBy));
		if (metrics.contains("registrationPaymentRate") && !dailyTaskOrLink) {
			throw new IllegalArgumentException("注册付费率仅适用于 Task/链接日报");
		}
		if (metrics.contains("costAmount") && !sensitive) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "无权查看敏感指标");
		}
		return new Validated(start, end, grain, groupBy, mediaLevel, requested.advertiserId(), requested.productId(),
				requested.appLinkId(), requested.taskId(), requested.channelId(), requested.mediaAccountId(),
				Set.copyOf(metrics), sort, direction, current, size);
	}

	private void requireExplicitScope(Validated query) {
		if (StringUtils.hasText(query.advertiserId())) {
			accessScope.require(ScopeType.ADVERTISER, query.advertiserId(), PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(query.channelId())) {
			accessScope.require(ScopeType.CHANNEL, query.channelId(), PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(query.mediaAccountId())) {
			accessScope.require(ScopeType.MEDIA_ACCOUNT, query.mediaAccountId(), PermissionLevel.VIEW);
		}
	}

	private void requireExplicitScope(Validated query, FrozenScope scope) {
		requireRestriction(scope.advertisers(), query.advertiserId());
		requireRestriction(scope.channels(), query.channelId());
		requireRestriction(scope.mediaAccounts(), query.mediaAccountId());
	}

	private void requireRestriction(Restriction restriction, String requestedId) {
		if (!StringUtils.hasText(requestedId) || restriction.unrestricted()) {
			return;
		}
		if (!restriction.ids().contains(requestedId)) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问");
		}
	}

	private ScopeClause scope(FrozenScope scope) {
		StringBuilder sql = new StringBuilder();
		Map<String, Object> parameters = new LinkedHashMap<>();
		appendRestriction(sql, parameters, "h.advertiser_id", "scopeAdvertisers", scope.advertisers());
		appendRestriction(sql, parameters, "h.channel_id", "scopeChannels", scope.channels());
		appendRestriction(sql, parameters, "h.media_account_id", "scopeMediaAccounts", scope.mediaAccounts());
		return new ScopeClause(sql.toString(), Map.copyOf(parameters));
	}

	private LocalDateTime watermark(Validated query, FrozenScope frozenScope) {
		ScopeClause scope = scope(frozenScope);
		MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("start", query.start().atStartOfDay())
			.addValue("end", query.end().plusDays(1).atStartOfDay());
		StringBuilder where = new StringBuilder(" WHERE h.time_hour>=:start AND h.time_hour<:end");
		appendFilter(where, parameters, "h.advertiser_id", "advertiserId", query.advertiserId());
		appendFilter(where, parameters, "h.product_id", "productId", query.productId());
		appendFilter(where, parameters, "h.app_link_id", "appLinkId", query.appLinkId());
		appendFilter(where, parameters, "h.task_id", "taskId", query.taskId());
		appendFilter(where, parameters, "h.channel_id", "channelId", query.channelId());
		appendFilter(where, parameters, "h.media_account_id", "mediaAccountId", query.mediaAccountId());
		where.append(scope.sql());
		scope.parameters().forEach(parameters::addValue);
		Timestamp value = jdbcTemplate.queryForObject("SELECT MAX(h.as_of) FROM scx_ocpx_hourly_report h" + where,
				parameters, Timestamp.class);
		return value == null ? null : value.toLocalDateTime();
	}

	private String scopeHash(FrozenScope scope) {
		String canonical = scope.advertisers() + "|" + scope.channels() + "|" + scope.mediaAccounts();
		try {
			return java.util.HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private FrozenScope freeze(OcpxAccessScope.Snapshot snapshot) {
		return new FrozenScope(snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW),
				snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW),
				snapshot.restriction(ScopeType.MEDIA_ACCOUNT, PermissionLevel.VIEW));
	}

	private void appendRestriction(StringBuilder sql, Map<String, Object> parameters, String column, String name,
			Restriction restriction) {
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			sql.append(" AND 1=0");
			return;
		}
		sql.append(" AND ").append(column).append(" IN (:").append(name).append(')');
		parameters.put(name, restriction.ids());
	}

	private List<UnifiedReportViews.Option> options(String baseSql, String scopeColumn, Restriction restriction) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		StringBuilder sql = new StringBuilder(baseSql);
		appendOptionRestriction(sql, parameters, scopeColumn, "scopeIds", restriction);
		sql.append(" ORDER BY 2,id");
		return jdbcTemplate.query(sql.toString(), parameters,
				(resultSet, rowNumber) -> new UnifiedReportViews.Option(resultSet.getString(1), resultSet.getString(2),
						resultSet.getString(3)));
	}

	private List<UnifiedReportViews.Option> optionsWithTaskScope(FrozenScope scope) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		StringBuilder sql = new StringBuilder("SELECT id,task_name,status FROM scx_ocpx_task WHERE status<>'ARCHIVED'");
		appendOptionRestriction(sql, parameters, "advertiser_id", "taskAdvertisers", scope.advertisers());
		appendOptionRestriction(sql, parameters, "channel_id", "taskChannels", scope.channels());
		appendOptionRestriction(sql, parameters, "media_account_id", "taskMedia", scope.mediaAccounts());
		sql.append(" ORDER BY task_name,id");
		return jdbcTemplate.query(sql.toString(), parameters,
				(resultSet, rowNumber) -> new UnifiedReportViews.Option(resultSet.getString(1), resultSet.getString(2),
						resultSet.getString(3)));
	}

	private List<UnifiedReportViews.Option> optionsWithMediaScope(FrozenScope scope) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		StringBuilder sql = new StringBuilder("SELECT id,name,status FROM scx_ocpx_media_account WHERE status='ACTIVE'");
		appendOptionRestriction(sql, parameters, "channel_id", "mediaChannels", scope.channels());
		appendOptionRestriction(sql, parameters, "id", "mediaAccounts", scope.mediaAccounts());
		sql.append(" ORDER BY name,id");
		return jdbcTemplate.query(sql.toString(), parameters,
				(resultSet, rowNumber) -> new UnifiedReportViews.Option(resultSet.getString(1), resultSet.getString(2),
						resultSet.getString(3)));
	}

	private void appendOptionRestriction(StringBuilder sql, MapSqlParameterSource parameters, String column,
			String name, Restriction restriction) {
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			sql.append(" AND 1=0");
			return;
		}
		sql.append(" AND ").append(column).append(" IN (:").append(name).append(')');
		parameters.addValue(name, restriction.ids());
	}

	private String dimensionExpression(Validated query) {
		String bucket = "HOUR".equals(query.grain()) ? "DATE_FORMAT(h.time_hour,'%Y-%m-%d %H:00:00')"
				: "DATE_FORMAT(h.time_hour,'%Y-%m-%d')";
		String object = switch (query.groupBy()) {
			case "TIME" -> null;
			case "PRODUCT" -> "COALESCE(h.product_id,'UNMAPPED')";
			case "LINK" -> "COALESCE(h.app_link_id,'UNMAPPED')";
			case "TASK" -> "COALESCE(h.task_id,'UNMAPPED')";
			case "MEDIA" -> "CHANNEL".equals(query.mediaLevel()) ? "COALESCE(h.channel_id,'UNMAPPED')"
					: "COALESCE(h.media_account_id,'UNMAPPED')";
			default -> throw new IllegalArgumentException("unsupported groupBy");
		};
		return object == null ? bucket : "CONCAT(" + bucket + ",'|'," + object + ")";
	}

	private String groupedSql(String dimension, String where) {
		return "SELECT " + dimension + " AS dimensionKey," + aggregateColumns() + " FROM scx_ocpx_hourly_report h"
				+ where + " GROUP BY " + dimension;
	}

	private String summarySql(String where) {
		return "SELECT " + aggregateColumns() + " FROM scx_ocpx_hourly_report h" + where;
	}

	private String aggregateColumns() {
		return "SUM(h.impressions) AS impressions,SUM(h.raw_clicks) AS rawClicks,SUM(h.valid_clicks) AS validClicks,"
				+ "SUM(h.duplicate_clicks) AS duplicateClicks,SUM(h.activate_count) AS activations,"
				+ "SUM(h.register_count) AS registrations,SUM(h.payment_count) AS payments,"
				+ "SUM(h.order_count) AS orders,SUM(h.cost_amount) AS costAmount,"
				+ "SUM(h.adjustment_count) AS adjustmentCount,SUM(h.deduction_count) AS deductionCount,"
				+ "MAX(h.as_of) AS asOf,CASE WHEN SUM(h.quality_status='UNMAPPED')>0 THEN 'UNMAPPED' ELSE 'COMPLETE' END AS qualityStatus,"
				+ "CASE WHEN SUM(h.finality='DELAYED')>0 THEN 'DELAYED' WHEN SUM(h.finality='PROVISIONAL')>0 "
				+ "THEN 'PROVISIONAL' ELSE 'FINAL' END AS finalityStatus";
	}

	private UnifiedReportViews.Record record(Map<String, Object> row, Validated query, boolean sensitive,
			boolean dailyTaskOrLink) {
		String key = string(row, "dimensionKey");
		Map<String, Object> dimensions = new LinkedHashMap<>();
		dimensions.put("key", key);
		dimensions.put("grain", query.grain());
		int separator = key == null ? -1 : key.indexOf('|');
		dimensions.put("bucket", separator < 0 ? key : key.substring(0, separator));
		if (separator >= 0) {
			dimensions.put("objectId", key.substring(separator + 1));
		}
		if ("MEDIA".equals(query.groupBy())) {
			dimensions.put("mediaLevel", query.mediaLevel());
		}
		return new UnifiedReportViews.Record(key, query.groupBy(), Collections.unmodifiableMap(dimensions),
				metricDictionary.select(base(row), query.metrics(), dailyTaskOrLink, sensitive),
				string(row, "qualityStatus"), string(row, "finalityStatus"), dateTime(value(row, "asOf")));
	}

	private Map<String, BigDecimal> base(Map<String, Object> row) {
		Map<String, BigDecimal> values = new LinkedHashMap<>();
		for (String metric : List.of("impressions", "rawClicks", "validClicks", "duplicateClicks", "activations",
				"registrations", "payments", "orders", "costAmount", "adjustmentCount", "deductionCount")) {
			Object value = value(row, metric);
			values.put(metric, value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()));
		}
		return values;
	}

	private Map<String, BigDecimal> sumBase(List<Map<String, Object>> rows) {
		Map<String, BigDecimal> sums = new LinkedHashMap<>();
		for (String metric : List.of("impressions", "rawClicks", "validClicks", "duplicateClicks", "activations",
				"registrations", "payments", "orders", "costAmount", "adjustmentCount", "deductionCount")) {
			BigDecimal sum = rows.stream()
				.map(this::base)
				.map(values -> values.getOrDefault(metric, BigDecimal.ZERO))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			sums.put(metric, sum);
		}
		return sums;
	}

	private LocalDateTime timestamp(List<Map<String, Object>> rows, String key) {
		return rows.stream()
			.map(row -> dateTime(value(row, key)))
			.filter(java.util.Objects::nonNull)
			.max(LocalDateTime::compareTo)
			.orElse(null);
	}

	private String freshness(List<Map<String, Object>> rows, LocalDateTime asOf) {
		if (rows.stream().anyMatch(row -> "DELAYED".equals(string(row, "finalityStatus")))) {
			return "DELAYED";
		}
		if (asOf != null && asOf.isBefore(LocalDateTime.now(REPORT_ZONE).minusHours(2))) {
			return "DELAYED";
		}
		return rows.stream().anyMatch(row -> "PROVISIONAL".equals(string(row, "finalityStatus"))) ? "PROVISIONAL"
				: "FINAL";
	}

	private Object value(Map<String, Object> row, String key) {
		if (row.containsKey(key)) {
			return row.get(key);
		}
		String lower = key.toLowerCase(Locale.ROOT);
		return row.entrySet()
			.stream()
			.filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(lower))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	private String string(Map<String, Object> row, String key) {
		Object value = value(row, key);
		return value == null ? null : value.toString();
	}

	private LocalDateTime dateTime(Object value) {
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime();
		}
		return null;
	}

	private void appendFilter(StringBuilder where, MapSqlParameterSource parameters, String column, String name,
			String value) {
		if (StringUtils.hasText(value)) {
			where.append(" AND ").append(column).append("=:").append(name);
			parameters.addValue(name, value);
		}
	}

	private String normalized(String value, String fallback, Set<String> allowed, String field) {
		String normalized = StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : fallback;
		if (!allowed.contains(normalized)) {
			throw new IllegalArgumentException(field + " 不在白名单内");
		}
		return normalized;
	}

	private record Validated(LocalDate start, LocalDate end, String grain, String groupBy, String mediaLevel,
			String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, Set<String> metrics, String sort, String direction, long current, long size) {
	}

	private record ScopeClause(String sql, Map<String, Object> parameters) {
	}

	record FrozenScope(Restriction advertisers, Restriction channels, Restriction mediaAccounts) {
	}

	record DashboardSession(FrozenScope scope, long scopeRevision, String scopeHash, LocalDateTime factWatermark,
			boolean sensitive, LocalDate minimumDate, LocalDate maximumDate) {
	}

}
