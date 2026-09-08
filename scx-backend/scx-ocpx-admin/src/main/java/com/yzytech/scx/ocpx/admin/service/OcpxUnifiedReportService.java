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
import com.yzytech.scx.ocpx.admin.mapper.OcpxUnifiedReportSqlMapper;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

	private final OcpxUnifiedReportSqlMapper sqlMapper;

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final Set<String> GROUPS = Set.of("TIME", "ADVERTISER", "PROMOTION_LINK", "MEDIA", "MONITOR_TASK");

	private static final List<String> METRICS = List.of("SHOW", "UNIQUE_SHOW", "CLICK", "UNIQUE_CLICK",
			"TARGET_CONVERSION", "CALLBACK", "DEDUCTION_PASSED", "DEDUCTION_DEDUCTED", "ACTIVATE", "EVOKE", "LOGIN",
			"REGISTER", "PAYMENT", "CUSTOMER_ACQUISITION", "NEXT_STAY", "VISITS", "AUTHENTICATION", "PLACE_ORDER",
			"FINISH_THE_ORDER", "FIRST_ACTIVATION", "FIRST_ORDER", "DOWNLOAD", "ACTIVITY", "ADDICTION",
			"REJECTED_CLICK");

	private static final List<String> SUMMARY_CSV_HEADER = List.of("报告时间", "媒体名称", "监测名称", "广告主名称", "链接名称", "下游配置放行率",
			"上游Callback数", "下游回传提交数", "SCX扣量拦截数", "SCX待判定数", "扣量统计起始时间", "去重展示数", "去重点击数", "点击率", "广告主消费", "上游激活数",
			"上游唤起数", "上游登录数", "上游注册数", "上游付费数", "上游获客数", "上游次留数", "上游访问数", "上游认证数", "上游下单数", "上游完单数", "上游首活数", "上游首购数",
			"上游下载数", "上游活动数", "上游深度使用数", "订单金额");

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
		Long total = sqlMapper.countSummary(query, Integer.parseInt(query.getStartTime() + "00"),
				Integer.parseInt(query.getEndTime() + "23"));
		List<SummaryReportViews.Row> rows = sqlMapper
			.selectSummary(query, Integer.parseInt(query.getStartTime() + "00"),
					Integer.parseInt(query.getEndTime() + "23"), true, query.getSize().intValue(),
					(int) ((query.getCurrent() - 1) * query.getSize()))
			.stream()
			.map(rs -> {
				Object ratio = rs.linkRadio();
				return new SummaryReportViews.Row(rs.reportTime(), rs.mId(), rs.mediaName(), rs.tId(), rs.monitorName(),
						rs.aId(), rs.advertiserName(), rs.lId(), rs.linkName(), ratio == null ? null : ratio.toString(),
						rs.callbackCount(), rs.deductionPassedCount(), rs.deductionDeductedCount(),
						Math.max(0, rs.callbackCount() - rs.deductionPassedCount() - rs.deductionDeductedCount()),
						rs.uniShowSmt(), rs.uniClkSmt(), rs.clickRate() == null ? null : rs.clickRate(), rs.basePrice(),
						rs.activate(), rs.evoke(), rs.login(), rs.register(), rs.payment(), rs.customerAcquisition(),
						rs.nextStay(), rs.visits(), rs.authentication(), rs.placeOrder(), rs.finishTheOrder(),
						rs.firstActivation(), rs.firstOrder(), rs.download(), rs.activity(), rs.addiction(),
						rs.currencyAmount());
			})
			.toList();
		LocalDateTime coverageStart = deductionCoverageStart(query);
		return new SummaryReportViews.Page(List.copyOf(rows), query.getCurrent(), query.getSize(),
				total == null ? 0 : total, coverageStart, deductionCoverageStatus(query, coverageStart));
	}

	public String summaryCsv(SummaryReportQuery input) {
		SummaryReportQuery query = normalizeSummary(input);
		validateFilterOwnership(summaryFilter(query));
		List<SummaryReportViews.Row> rows = sqlMapper
			.selectSummary(query, Integer.parseInt(query.getStartTime() + "00"),
					Integer.parseInt(query.getEndTime() + "23"), false, 0, 0)
			.stream()
			.map(rs -> {
				Object ratio = rs.linkRadio();
				return new SummaryReportViews.Row(rs.reportTime(), rs.mId(), rs.mediaName(), rs.tId(), rs.monitorName(),
						rs.aId(), rs.advertiserName(), rs.lId(), rs.linkName(), ratio == null ? null : ratio.toString(),
						rs.callbackCount(), rs.deductionPassedCount(), rs.deductionDeductedCount(),
						Math.max(0, rs.callbackCount() - rs.deductionPassedCount() - rs.deductionDeductedCount()),
						rs.uniShowSmt(), rs.uniClkSmt(), rs.clickRate() == null ? null : rs.clickRate(), rs.basePrice(),
						rs.activate(), rs.evoke(), rs.login(), rs.register(), rs.payment(), rs.customerAcquisition(),
						rs.nextStay(), rs.visits(), rs.authentication(), rs.placeOrder(), rs.finishTheOrder(),
						rs.firstActivation(), rs.firstOrder(), rs.download(), rs.activity(), rs.addiction(),
						rs.currencyAmount());
			})
			.toList();
		if (rows.isEmpty()) {
			return "";
		}
		LocalDateTime coverageStart = deductionCoverageStart(query);
		return OcpxCsv.content(SUMMARY_CSV_HEADER,
				rows.stream().map(row -> summaryCsvRow(row, coverageStart)).toList());
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
		return sqlMapper.deductionCoverageStart(query);
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
		return new UnifiedReportViews.Options(sqlMapper.selectAdvertiserOptions(scopedAdvertiserId),
				sqlMapper.selectLinkOptions(scopedAdvertiserId), sqlMapper.selectMediaOptions(scopedAdvertiserId),
				sqlMapper.selectTaskOptions(scopedAdvertiserId, null));
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
		var activity = sqlMapper.accountActivity(advertiserId, startHour, endHour);
		return new UnifiedReportViews.Account(advertiserId, advertiserAccountService.balance(advertiserId),
				activity == null ? BigDecimal.ZERO.setScale(6) : activity.todaySpend(),
				activity == null ? 0 : activity.rejectedClickCount(),
				activity == null ? 0 : activity.rejectedConversionCount());
	}

	private LocalDateTime aggregateWatermark(UnifiedReportQuery query) {
		return sqlMapper.aggregateWatermark(query, hourKey(query.getStartDate(), "00"),
				hourKey(query.getEndDate(), "23"));
	}

	private void validateFilterOwnership(UnifiedReportQuery query) {
		if (StringUtils.hasText(query.getAdvertiserId()))
			requireExists(sqlMapper.countAdvertiser(query.getAdvertiserId()), "广告主不存在");
		if (StringUtils.hasText(query.getPromotionLinkId()))
			requireExists(sqlMapper.countLink(query), "推广链接与广告主不匹配");
		if (StringUtils.hasText(query.getMediaId()))
			requireExists(sqlMapper.countMedia(query.getMediaId()), "媒体不存在");
		if (StringUtils.hasText(query.getMonitorTaskId()))
			requireExists(sqlMapper.countTask(query), "监测任务与筛选对象不匹配");
		else if (specified(query.getAdvertiserId(), query.getPromotionLinkId(), query.getMediaId()) >= 2)
			requireExists(sqlMapper.countTask(query), "筛选对象之间不存在有效监测任务关系");
	}

	private void requireExists(Long count, String message) {
		if (count == null || count == 0)
			throw new BootBusinessException(message);
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
		List<MetricRow> rows = new ArrayList<>();
		for (var value : sqlMapper.aggregate(query, hourKey(query.getStartDate(), "00"),
				hourKey(query.getEndDate(), "23"))) {
			for (String code : METRICS)
				rows.add(new MetricRow(value.dimensionKey(), value.dimensionName(), code, value.counts().get(code),
						BigDecimal.ZERO));
			rows.add(new MetricRow(value.dimensionKey(), value.dimensionName(), "BASE_PRICE", 0, value.spendAmount()));
			rows.add(new MetricRow(value.dimensionKey(), value.dimensionName(), "CURRENCY_AMOUNT", 0,
					value.currencyAmount()));
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
	private static final class MetricRow {

		private final String key;

		private final String name;

		private final String metricCode;

		private final long count;

		private final BigDecimal amount;

	}

	private record AccountActivity(BigDecimal todaySpend, long rejectedClickCount, long rejectedConversionCount) {
	}

}
