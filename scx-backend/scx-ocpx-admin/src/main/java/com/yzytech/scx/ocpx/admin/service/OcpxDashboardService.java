package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxReportingProperties;
import com.yzytech.scx.ocpx.admin.dto.DashboardOverviewViews;
import com.yzytech.scx.ocpx.admin.dto.DashboardQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcpxDashboardService {

	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	private static final List<String> METRICS = List.of("impressions", "validClicks", "activations", "registrations",
			"payments", "activationRate", "registrationRate", "paymentRate");

	private static final List<String> CARD_METRICS = List.of("validClicks", "activations", "activationRate",
			"registrations", "payments");

	private static final List<String> RANKING_BASE_METRICS = List.of("impressions", "validClicks", "activations",
			"registrations", "payments");

	private static final int MAXIMUM_RANKING_SOURCE_ROWS = 10000;

	private final OcpxUnifiedReportService reportService;

	private final OcpxMetricDictionary metricDictionary;

	private final OcpxDashboardCache cache;

	private final OcpxReportingProperties properties;

	public DashboardOverviewViews.Overview overview(DashboardQuery requested) {
		DateRange range = resolve(requested);
		long length = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
		LocalDate comparisonEnd = range.start().minusDays(1);
		LocalDate comparisonStart = comparisonEnd.minusDays(length - 1);
		UnifiedReportQuery watermarkProbe = reportQuery(requested, comparisonStart, range.end(), "TIME", "dimension",
				"ASC", 1, 1);
		OcpxUnifiedReportService.DashboardSession session = reportService.openDashboardSession(watermarkProbe);
		String cacheKey = cacheKey(currentUserId(), requested, range, session);
		try {
			Optional<DashboardOverviewViews.Overview> cached = cache.get(cacheKey);
			if (cached.isPresent()) {
				return withCacheHit(cached.get());
			}
		}
		catch (RuntimeException cacheFailure) {
			log.warn("Dashboard cache read failed; falling back to unified report query");
		}

		List<UnifiedReportViews.Page> successfulPages = new ArrayList<>();
		SectionResult<List<DashboardOverviewViews.MetricCard>> cards = section(
				() -> cards(requested, range, comparisonStart, comparisonEnd, session), successfulPages);
		SectionResult<List<DashboardOverviewViews.TrendPoint>> trend = section(() -> trend(requested, range, session),
				successfulPages);
		SectionResult<DashboardOverviewViews.Ranking> products = section(
				() -> ranking(requested, range, session, "PRODUCT"), successfulPages);
		SectionResult<DashboardOverviewViews.Ranking> tasks = section(() -> ranking(requested, range, session, "TASK"),
				successfulPages);
		SectionResult<DashboardOverviewViews.Ranking> media = section(() -> ranking(requested, range, session, "MEDIA"),
				successfulPages);

		boolean partialFailure = List
			.of(cards.section(), trend.section(), products.section(), tasks.section(), media.section())
			.stream()
			.anyMatch(this::failed);
		LocalDateTime asOf = successfulPages.stream()
			.map(UnifiedReportViews.Page::asOf)
			.filter(java.util.Objects::nonNull)
			.min(LocalDateTime::compareTo)
			.orElse(null);
		String freshness = mergeFreshness(successfulPages);
		List<UnifiedReportViews.MetricDefinition> definitions = successfulPages.stream()
			.map(UnifiedReportViews.Page::metricDefinitions)
			.filter(list -> list != null && !list.isEmpty())
			.findFirst()
			.orElseGet(() -> metricDictionary.definitions(false, session.sensitive()));
		DashboardOverviewViews.Overview result = new DashboardOverviewViews.Overview(range.end(), range.start(),
				range.end(), REPORT_ZONE.getId(), asOf, freshness, OcpxMetricDictionary.VERSION, session.scopeRevision(),
				false, partialFailure, definitions, cards.section(), trend.section(), products.section(),
				tasks.section(), media.section());
		if (!partialFailure) {
			try {
				cache.put(cacheKey, result, properties.getDashboardCacheTtlSeconds());
			}
			catch (RuntimeException cacheFailure) {
				log.warn("Dashboard cache write failed; returning unified report result");
			}
		}
		return result;
	}

	public UnifiedReportViews.Options options() {
		return reportService.optionsForDashboard();
	}

	private Payload<List<DashboardOverviewViews.MetricCard>> cards(DashboardQuery requested, DateRange range,
			LocalDate comparisonStart, LocalDate comparisonEnd, OcpxUnifiedReportService.DashboardSession session) {
		UnifiedReportViews.Page current = reportService.queryForDashboard(reportQuery(requested, range.start(),
				range.end(), "TIME", "dimension", "ASC", 1, properties.getMaximumPageSize()), session);
		if (current.total() == 0) {
			return new Payload<>(List.of(), List.of(current), true);
		}
		UnifiedReportViews.Page comparison = reportService.queryForDashboard(reportQuery(requested, comparisonStart,
				comparisonEnd, "TIME", "dimension", "ASC", 1, properties.getMaximumPageSize()), session);
		Map<String, String> labels = new LinkedHashMap<>();
		current.metricDefinitions().forEach(definition -> labels.put(definition.code(), definition.label()));
		List<DashboardOverviewViews.MetricCard> result = CARD_METRICS.stream().map(metric -> {
			UnifiedReportViews.MetricValue value = current.querySummary().metrics().get(metric);
			UnifiedReportViews.MetricValue previous = comparison.total() == 0 ? null
					: comparison.querySummary().metrics().get(metric);
			return new DashboardOverviewViews.MetricCard(metric, labels.getOrDefault(metric, metric), value, previous,
					metricDictionary.comparisonChange(value, previous),
					"上一等长区间 " + comparisonStart + " 至 " + comparisonEnd);
		}).toList();
		return new Payload<>(result, List.of(current), false);
	}

	private Payload<List<DashboardOverviewViews.TrendPoint>> trend(DashboardQuery requested, DateRange range,
			OcpxUnifiedReportService.DashboardSession session) {
		UnifiedReportViews.Page page = reportService.queryForDashboard(reportQuery(requested, range.start(),
				range.end(), "TIME", "dimension", "ASC", 1, properties.getMaximumPageSize()), session);
		List<DashboardOverviewViews.TrendPoint> points = page.records()
			.stream()
			.map(record -> new DashboardOverviewViews.TrendPoint(String.valueOf(record.dimensions().get("bucket")),
					record.metrics(), record.finalityStatus(), record.asOf()))
			.toList();
		return new Payload<>(points, List.of(page), points.isEmpty());
	}

	private Payload<DashboardOverviewViews.Ranking> ranking(DashboardQuery requested, DateRange range,
			OcpxUnifiedReportService.DashboardSession session, String groupBy) {
		List<UnifiedReportViews.Page> pages = rankingPages(requested, range, session, groupBy);
		Map<String, RankingAccumulator> totals = new LinkedHashMap<>();
		pages.stream().flatMap(page -> page.records().stream()).forEach(record -> {
			String objectId = String.valueOf(record.dimensions().get("objectId"));
			totals.computeIfAbsent(objectId, ignored -> new RankingAccumulator()).add(record);
		});
		List<DashboardOverviewViews.RankingItem> records = totals.entrySet()
			.stream()
			.map(entry -> rankingItem(groupBy, entry.getKey(), entry.getValue(), session.sensitive()))
			.sorted(Comparator
				.comparing((DashboardOverviewViews.RankingItem item) -> rankingMetric(item, "validClicks"))
				.reversed()
				.thenComparing(DashboardOverviewViews.RankingItem::objectId))
			.limit(10)
			.toList();
		return new Payload<>(new DashboardOverviewViews.Ranking(groupBy, records), pages, records.isEmpty());
	}

	private List<UnifiedReportViews.Page> rankingPages(DashboardQuery requested, DateRange range,
			OcpxUnifiedReportService.DashboardSession session, String groupBy) {
		long size = properties.getMaximumPageSize();
		List<UnifiedReportViews.Page> pages = new ArrayList<>();
		UnifiedReportViews.Page first = reportService.queryForDashboard(
				reportQuery(requested, range.start(), range.end(), groupBy, "dimension", "ASC", 1, size), session);
		pages.add(first);
		if (first.total() > MAXIMUM_RANKING_SOURCE_ROWS) {
			throw new IllegalStateException("Dashboard 排行源记录超过同步查询上限");
		}
		long pageCount = (first.total() + size - 1) / size;
		for (long page = 2; page <= pageCount; page++) {
			pages.add(reportService.queryForDashboard(
					reportQuery(requested, range.start(), range.end(), groupBy, "dimension", "ASC", page, size),
					session));
		}
		return List.copyOf(pages);
	}

	private DashboardOverviewViews.RankingItem rankingItem(String groupBy, String objectId,
			RankingAccumulator accumulator, boolean sensitive) {
		return new DashboardOverviewViews.RankingItem(groupBy + '|' + objectId, objectId,
				metricDictionary.select(accumulator.values(), Set.copyOf(METRICS), false, sensitive),
				accumulator.freshness(), accumulator.asOf());
	}

	private BigDecimal rankingMetric(DashboardOverviewViews.RankingItem item, String metric) {
		UnifiedReportViews.MetricValue value = item.metrics().get(metric);
		return value == null || value.value() == null ? BigDecimal.ZERO : value.value();
	}

	private <T> SectionResult<T> section(SectionSupplier<T> supplier, List<UnifiedReportViews.Page> successfulPages) {
		try {
			Payload<T> payload = supplier.get();
			successfulPages.addAll(payload.pages());
			return new SectionResult<>(payload.empty() ? DashboardOverviewViews.Section.empty(payload.data())
					: DashboardOverviewViews.Section.ready(payload.data()));
		}
		catch (BootBusinessException forbidden) {
			throw forbidden;
		}
		catch (RuntimeException error) {
			String traceId = UUID.randomUUID().toString();
			String status = error instanceof QueryTimeoutException ? "TIMEOUT" : "ERROR";
			log.warn("Dashboard section failed traceId={} errorType={}", traceId, error.getClass().getSimpleName());
			return new SectionResult<>(
					DashboardOverviewViews.Section.failed(status, "DASHBOARD_SECTION_FAILED", traceId));
		}
	}

	private UnifiedReportQuery reportQuery(DashboardQuery requested, LocalDate start, LocalDate end, String groupBy,
			String sort, String direction, long current, long size) {
		return new UnifiedReportQuery(start, end, "DAY", groupBy, "ACCOUNT", requested.advertiserId(),
				requested.productId(), requested.appLinkId(), requested.taskId(), requested.channelId(),
				requested.mediaAccountId(), METRICS, sort, direction, current, size);
	}

	private DateRange resolve(DashboardQuery requested) {
		LocalDate end = requested.endDate() == null ? LocalDate.now(REPORT_ZONE) : requested.endDate();
		LocalDate start = requested.startDate() == null ? end.minusDays(properties.getDefaultQueryDays() - 1L)
				: requested.startDate();
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能晚于结束日期");
		}
		if (start.plusDays(properties.getMaximumQueryDays() - 1L).isBefore(end)) {
			throw new IllegalArgumentException("查询跨度超过允许上限");
		}
		return new DateRange(start, end);
	}

	private String currentUserId() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问");
		}
		return user.getUserId();
	}

	private String cacheKey(String userId, DashboardQuery requested, DateRange range,
			OcpxUnifiedReportService.DashboardSession session) {
		String canonical = userId + '|' + session.scopeHash() + '|' + session.scopeRevision() + '|' + range.start()
				+ '|' + range.end() + '|' + value(requested.advertiserId()) + '|' + value(requested.productId()) + '|'
				+ value(requested.appLinkId()) + '|' + value(requested.taskId()) + '|' + value(requested.channelId())
				+ '|' + value(requested.mediaAccountId()) + '|' + OcpxMetricDictionary.VERSION + '|'
				+ value(session.factWatermark());
		try {
			return java.util.HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private String value(Object value) {
		return value == null ? "" : value.toString();
	}

	private boolean failed(DashboardOverviewViews.Section<?> section) {
		return "ERROR".equals(section.status()) || "TIMEOUT".equals(section.status());
	}

	private String mergeFreshness(List<UnifiedReportViews.Page> pages) {
		if (pages.isEmpty() || pages.stream().anyMatch(page -> "DELAYED".equals(page.freshnessStatus()))) {
			return "DELAYED";
		}
		return pages.stream().anyMatch(page -> "PROVISIONAL".equals(page.freshnessStatus())) ? "PROVISIONAL" : "FINAL";
	}

	private DashboardOverviewViews.Overview withCacheHit(DashboardOverviewViews.Overview value) {
		return new DashboardOverviewViews.Overview(value.businessDate(), value.startDate(), value.endDate(),
				value.timezone(), value.asOf(), value.freshnessStatus(), value.metricDefinitionsVersion(),
				value.scopeRevision(), true, value.partialFailure(), value.metricDefinitions(), value.cards(),
				value.trend(), value.productRanking(), value.taskRanking(), value.mediaRanking());
	}

	private record DateRange(LocalDate start, LocalDate end) {
	}

	private record Payload<T>(T data, List<UnifiedReportViews.Page> pages, boolean empty) {
	}

	private record SectionResult<T>(DashboardOverviewViews.Section<T> section) {
	}

	private static final class RankingAccumulator {

		private final Map<String, BigDecimal> values = new LinkedHashMap<>();

		private LocalDateTime asOf;

		private String freshness = "FINAL";

		private void add(UnifiedReportViews.Record record) {
			for (String metric : RANKING_BASE_METRICS) {
				UnifiedReportViews.MetricValue value = record.metrics().get(metric);
				if (value != null && value.value() != null) {
					values.merge(metric, value.value(), BigDecimal::add);
				}
			}
			if (record.asOf() != null && (asOf == null || record.asOf().isBefore(asOf))) {
				asOf = record.asOf();
			}
			if ("DELAYED".equals(record.finalityStatus())) {
				freshness = "DELAYED";
			}
			else if ("PROVISIONAL".equals(record.finalityStatus()) && "FINAL".equals(freshness)) {
				freshness = "PROVISIONAL";
			}
		}

		private Map<String, BigDecimal> values() {
			return values;
		}

		private LocalDateTime asOf() {
			return asOf;
		}

		private String freshness() {
			return freshness;
		}

	}

	@FunctionalInterface
	private interface SectionSupplier<T> {

		Payload<T> get();

	}

}
