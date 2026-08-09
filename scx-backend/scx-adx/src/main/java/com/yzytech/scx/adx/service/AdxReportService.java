package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.dto.DashboardCoverageRow;
import com.yzytech.scx.adx.dto.DashboardDailyRow;
import com.yzytech.scx.adx.dto.DashboardViews;
import com.yzytech.scx.adx.mapper.AdxHourlyReportMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdxReportService {

	private static final Set<String> GRANULARITIES = Set.of("HOUR", "DATE", "MONTH", "YEAR");

	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	private static final String[] HEADERS = { "时间", "渠道名称", "监测链接", "广告主", "应用链接", "扣量比例", "展示数", "点击数", "有效点击", "重复点击",
			"跳过去重", "点击覆盖", "点击率(%)", "消耗", "原始转化", "实际回传", "扣除转化", "投递失败", "激活", "激活率(%)", "首活", "首活率(%)", "拉活",
			"拉活率(%)", "唤起", "登录", "注册", "付费", "获客", "二留", "三留", "四留", "五留", "六留", "七留", "访问", "认证", "下单", "首单", "完单" };

	private final AdxHourlyReportMapper reportMapper;

	private final AdxAccessScope accessScope;

	public Page<ReportRow> summary(Page<ReportRow> page, String granularity, String channelId, String monitorId,
			String advertiserId, String appLinkId, LocalDate startDate, LocalDate endDate) {
		String normalized = normalizeGranularity(granularity);
		LocalDate start = startDate == null ? LocalDate.now().minusDays(6) : startDate;
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能晚于结束日期");
		}
		if (start.plusYears(2).isBefore(end)) {
			throw new IllegalArgumentException("单次查询范围不能超过两年");
		}
		String scopedAdvertiserId = accessScope.constrainAdvertiser(advertiserId);
		return reportMapper.summary(page, normalized, channelId, monitorId, scopedAdvertiserId, appLinkId,
				start.atStartOfDay(), end.plusDays(1).atStartOfDay());
	}

	public DashboardViews.Summary dashboard(LocalDate startDate, LocalDate endDate, String channelId,
			String advertiserId, String appLinkId, String monitorId) {
		LocalDate today = LocalDate.now(REPORT_ZONE);
		LocalDate start = startDate == null ? today.minusDays(29) : startDate;
		LocalDate end = endDate == null ? today : endDate;
		validateDashboardRange(start, end, today);
		String scopedAdvertiserId = accessScope.constrainAdvertiser(advertiserId);
		LocalDate queryStart = start.isBefore(today.minusDays(2)) ? start : today.minusDays(2);
		LocalDate queryEnd = end.isAfter(today) ? end : today;
		List<DashboardDailyRow> source = reportMapper.dashboardDaily(queryStart.atStartOfDay(),
				queryEnd.plusDays(1).atStartOfDay(), channelId, scopedAdvertiserId, appLinkId, monitorId);
		Map<LocalDate, DashboardDailyRow> indexed = new HashMap<>();
		for (DashboardDailyRow row : source) {
			indexed.put(row.getReportDate(), row);
		}
		List<DashboardViews.DailyPoint> trend = new ArrayList<>();
		DashboardDailyRow totalsRow = new DashboardDailyRow();
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			DashboardDailyRow row = indexed.getOrDefault(date, emptyRow(date));
			merge(totalsRow, row);
			trend.add(new DashboardViews.DailyPoint(date, metrics(row)));
		}
		DashboardViews.MetricSet totals = metrics(totalsRow);
		DashboardCoverageRow coverageRow = reportMapper.dashboardCoverage(channelId, scopedAdvertiserId, appLinkId,
				monitorId);
		DashboardViews.Filters filters = new DashboardViews.Filters(start, end, channelId, scopedAdvertiserId,
				appLinkId, monitorId);
		LocalDateTime dataUpdatedAt = source.stream()
			.map(DashboardDailyRow::getDataUpdatedAt)
			.filter(java.util.Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return new DashboardViews.Summary(REPORT_ZONE.getId(), LocalDateTime.now(REPORT_ZONE), dataUpdatedAt, filters,
				coverage(totals), highlights(indexed, today), totals, trend, funnel(totals), retention(trend),
				configuration(coverageRow));
	}

	public DashboardViews.AccountSummary accountSummary(LocalDate startDate, LocalDate endDate, String channelId,
			String advertiserId, String appLinkId, String monitorId) {
		LocalDate today = LocalDate.now(REPORT_ZONE);
		LocalDate start = startDate == null ? today.minusDays(29) : startDate;
		LocalDate end = endDate == null ? today : endDate;
		validateDashboardRange(start, end, today);
		String scopedAdvertiserId = accessScope.constrainAdvertiser(advertiserId);
		Map<String, Object> values = reportMapper.accountSummary(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
				channelId, scopedAdvertiserId, appLinkId, monitorId);
		return new DashboardViews.AccountSummary("CNY", decimal(values, "totalBalance"),
				decimal(values, "rechargeAmount"), decimal(values, "cpcSpend"), number(values, "rejectedCharges"),
				LocalDateTime.now(REPORT_ZONE));
	}

	public byte[] export(String granularity, String channelId, String monitorId, String advertiserId, String appLinkId,
			LocalDate startDate, LocalDate endDate) throws IOException {
		List<ReportRow> rows = summary(new Page<>(1, 100_000, false), granularity, channelId, monitorId, advertiserId,
				appLinkId, startDate, endDate)
			.getRecords();
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("时段报表");
			Row header = sheet.createRow(0);
			for (int index = 0; index < HEADERS.length; index++) {
				header.createCell(index).setCellValue(HEADERS[index]);
			}
			for (int index = 0; index < rows.size(); index++) {
				writeRow(sheet.createRow(index + 1), rows.get(index));
			}
			sheet.createFreezePane(0, 1);
			for (int index = 0; index < HEADERS.length; index++) {
				sheet.setColumnWidth(index, index < 6 ? 5200 : 3200);
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private String normalizeGranularity(String value) {
		String normalized = StringUtils.hasText(value) ? value.toUpperCase() : "DATE";
		if (!GRANULARITIES.contains(normalized)) {
			throw new IllegalArgumentException("显示类型无效");
		}
		return normalized;
	}

	private BigDecimal decimal(Map<String, Object> source, String key) {
		Object value = source == null ? null : source.get(key);
		return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
	}

	private long number(Map<String, Object> source, String key) {
		Object value = source == null ? null : source.get(key);
		return value == null ? 0 : new BigDecimal(value.toString()).longValue();
	}

	private void validateDashboardRange(LocalDate start, LocalDate end, LocalDate today) {
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能晚于结束日期");
		}
		if (end.isAfter(today)) {
			throw new IllegalArgumentException("结束日期不能晚于今天");
		}
		if (start.plusDays(89).isBefore(end)) {
			throw new IllegalArgumentException("仪表盘单次查询范围不能超过90天");
		}
	}

	private DashboardViews.Highlights highlights(Map<LocalDate, DashboardDailyRow> indexed, LocalDate today) {
		DashboardDailyRow todayRow = indexed.getOrDefault(today, emptyRow(today));
		DashboardDailyRow yesterday = indexed.getOrDefault(today.minusDays(1), emptyRow(today.minusDays(1)));
		DashboardDailyRow beforeYesterday = indexed.getOrDefault(today.minusDays(2), emptyRow(today.minusDays(2)));
		return new DashboardViews.Highlights(comparison(l(todayRow.getClicks()), l(yesterday.getClicks()), "较昨日"),
				comparison(l(todayRow.getActivates()), l(yesterday.getActivates()), "较昨日"),
				comparison(l(yesterday.getRetentionD2()), l(beforeYesterday.getRetentionD2()), "较前日"),
				comparison(l(todayRow.getFirstActivations()), l(yesterday.getFirstActivations()), "较昨日"),
				comparison(l(todayRow.getPayments()), l(yesterday.getPayments()), "较昨日"));
	}

	private DashboardViews.ComparisonMetric comparison(long current, long previous, String label) {
		BigDecimal rate = previous == 0 ? null
				: BigDecimal.valueOf(current - previous)
					.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
		return new DashboardViews.ComparisonMetric(current, previous, rate, label);
	}

	private DashboardViews.DataCoverage coverage(DashboardViews.MetricSet totals) {
		boolean complete = totals.classifiedClicks() == totals.clicks();
		String status = !complete ? "INCOMPLETE_HISTORY"
				: totals.eligibleClicks() == 0 ? "NO_ELIGIBLE_CLICK" : "COMPLETE";
		return new DashboardViews.DataCoverage(complete, totals.clicks(), totals.classifiedClicks(), status,
				"UPSTREAM_REPORTED_EVENT");
	}

	private DashboardViews.ConfigurationCoverage configuration(DashboardCoverageRow row) {
		if (row == null) {
			return new DashboardViews.ConfigurationCoverage(0, 0, 0, 0, 0, 0);
		}
		return new DashboardViews.ConfigurationCoverage(l(row.getActiveChannels()), l(row.getActiveAdvertisers()),
				l(row.getActiveAppLinks()), l(row.getPublishedRoutes()), l(row.getDraftRoutes()),
				l(row.getAbnormalRoutes()));
	}

	private List<DashboardViews.FunnelStage> funnel(DashboardViews.MetricSet totals) {
		return List.of(new DashboardViews.FunnelStage("eligibleClicks", "有效点击", totals.eligibleClicks(), null),
				new DashboardViews.FunnelStage("activations", "激活", totals.activations(),
						ratio(totals.activations(), totals.eligibleClicks())),
				new DashboardViews.FunnelStage("registrations", "注册", totals.registrations(),
						ratio(totals.registrations(), totals.activations())),
				new DashboardViews.FunnelStage("payments", "付费", totals.payments(),
						ratio(totals.payments(), totals.registrations())));
	}

	private List<DashboardViews.RetentionPoint> retention(List<DashboardViews.DailyPoint> trend) {
		List<DashboardViews.RetentionPoint> points = new ArrayList<>();
		for (DashboardViews.DailyPoint point : trend) {
			DashboardViews.MetricSet m = point.metrics();
			long[] values = { m.retentionD2(), m.retentionD3(), m.retentionD4(), m.retentionD5(), m.retentionD6(),
					m.retentionD7() };
			for (int index = 0; index < values.length; index++) {
				points.add(new DashboardViews.RetentionPoint(point.reportDate(), "D" + (index + 2), values[index]));
			}
		}
		return points;
	}

	private DashboardViews.MetricSet metrics(DashboardDailyRow row) {
		long clicks = l(row.getClicks());
		long eligible = l(row.getEligibleClicks());
		boolean complete = l(row.getClassifiedClicks()) == clicks;
		return new DashboardViews.MetricSet(l(row.getImpressions()), clicks, eligible, l(row.getDeduplicatedClicks()),
				l(row.getSkippedClicks()), l(row.getClassifiedClicks()),
				row.getCostAmount() == null ? BigDecimal.ZERO : row.getCostAmount(), l(row.getActivates()),
				l(row.getFirstActivations()), l(row.getReactivations()), l(row.getRegistrations()),
				l(row.getPayments()), l(row.getRetentionD2()), l(row.getRetentionD3()), l(row.getRetentionD4()),
				l(row.getRetentionD5()), l(row.getRetentionD6()), l(row.getRetentionD7()), l(row.getRawConversions()),
				l(row.getForwardedConversions()), l(row.getDeductedConversions()), l(row.getDeliveryFailures()),
				complete ? ratio(l(row.getActivates()), eligible) : null,
				complete ? ratio(l(row.getFirstActivations()), eligible) : null,
				complete ? ratio(l(row.getReactivations()), eligible) : null);
	}

	private BigDecimal ratio(long value, long denominator) {
		return denominator == 0 ? null
				: BigDecimal.valueOf(value)
					.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
	}

	private void merge(DashboardDailyRow target, DashboardDailyRow value) {
		target.setImpressions(l(target.getImpressions()) + l(value.getImpressions()));
		target.setClicks(l(target.getClicks()) + l(value.getClicks()));
		target.setEligibleClicks(l(target.getEligibleClicks()) + l(value.getEligibleClicks()));
		target.setDeduplicatedClicks(l(target.getDeduplicatedClicks()) + l(value.getDeduplicatedClicks()));
		target.setSkippedClicks(l(target.getSkippedClicks()) + l(value.getSkippedClicks()));
		target.setClassifiedClicks(l(target.getClassifiedClicks()) + l(value.getClassifiedClicks()));
		target.setCostAmount((target.getCostAmount() == null ? BigDecimal.ZERO : target.getCostAmount())
			.add(value.getCostAmount() == null ? BigDecimal.ZERO : value.getCostAmount()));
		target.setActivates(l(target.getActivates()) + l(value.getActivates()));
		target.setFirstActivations(l(target.getFirstActivations()) + l(value.getFirstActivations()));
		target.setReactivations(l(target.getReactivations()) + l(value.getReactivations()));
		target.setRegistrations(l(target.getRegistrations()) + l(value.getRegistrations()));
		target.setPayments(l(target.getPayments()) + l(value.getPayments()));
		target.setRetentionD2(l(target.getRetentionD2()) + l(value.getRetentionD2()));
		target.setRetentionD3(l(target.getRetentionD3()) + l(value.getRetentionD3()));
		target.setRetentionD4(l(target.getRetentionD4()) + l(value.getRetentionD4()));
		target.setRetentionD5(l(target.getRetentionD5()) + l(value.getRetentionD5()));
		target.setRetentionD6(l(target.getRetentionD6()) + l(value.getRetentionD6()));
		target.setRetentionD7(l(target.getRetentionD7()) + l(value.getRetentionD7()));
		target.setRawConversions(l(target.getRawConversions()) + l(value.getRawConversions()));
		target.setForwardedConversions(l(target.getForwardedConversions()) + l(value.getForwardedConversions()));
		target.setDeductedConversions(l(target.getDeductedConversions()) + l(value.getDeductedConversions()));
		target.setDeliveryFailures(l(target.getDeliveryFailures()) + l(value.getDeliveryFailures()));
	}

	private DashboardDailyRow emptyRow(LocalDate date) {
		DashboardDailyRow row = new DashboardDailyRow();
		row.setReportDate(date);
		return row;
	}

	private long l(Long value) {
		return value == null ? 0 : value;
	}

	private void writeRow(Row row, ReportRow value) {
		Object[] cells = { value.getTimeBucket(), value.getChannelName(), value.getMonitorName(),
				value.getAdvertiserName(), value.getAppLinkName(), value.getDeductionRatio(), value.getImpressions(),
				value.getClicks(), value.getEligibleClickCount(), value.getDeduplicatedClickCount(),
				value.getSkippedClickCount(), value.getClassifiedClickCount(), value.getClickRate(),
				value.getCostAmount(), value.getRawConversionCount(), value.getForwardedConversionCount(),
				value.getDeductedConversionCount(), value.getDeliveryFailureCount(), value.getActivateCount(),
				value.getActivateRate(), value.getFirstActivateCount(), value.getFirstActivateRate(),
				value.getReactivationCount(), value.getReactivationRate(), value.getEvokeCount(), value.getLoginCount(),
				value.getRegisterCount(), value.getPaymentCount(), value.getAcquisitionCount(),
				value.getNextStayCount(), value.getRetention3dCount(), value.getRetention4dCount(),
				value.getRetention5dCount(), value.getRetention6dCount(), value.getRetention7dCount(),
				value.getVisitCount(), value.getAuthenticationCount(), value.getOrderCount(),
				value.getFirstOrderCount(), value.getFinishOrderCount() };
		for (int index = 0; index < cells.length; index++) {
			row.createCell(index).setCellValue(cells[index] == null ? "" : cells[index].toString());
		}
	}

}
