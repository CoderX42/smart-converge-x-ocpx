package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.DashboardViews;
import com.yzytech.scx.ocpx.admin.dto.ReportRow;
import com.yzytech.scx.ocpx.admin.mapper.OcpxHourlyReportMapper;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.PermissionLevel;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Restriction;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.ScopeType;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Snapshot;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxReportService {

	private static final Set<String> GRANULARITIES = Set.of("HOUR", "DATE", "MONTH", "YEAR");

	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	private static final String[] HEADERS = { "时间", "渠道名称", "监测链接", "广告主", "应用链接", "扣量比例", "展示数", "点击数", "有效点击", "重复点击",
			"跳过去重", "点击覆盖", "点击率(%)", "消耗", "原始转化", "实际回传", "扣除转化", "投递失败", "激活", "激活率(%)", "首活", "首活率(%)", "拉活",
			"拉活率(%)", "唤起", "登录", "注册", "付费", "获客", "二留", "三留", "四留", "五留", "六留", "七留", "访问", "认证", "下单", "首单", "完单" };

	private final OcpxHourlyReportMapper reportMapper;

	private final OcpxAccessScope accessScope;

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
		ScopeFilters scope = scopeFilters(channelId, advertiserId);
		return reportMapper.summary(page, normalized, channelId, monitorId, advertiserId, appLinkId,
				scope.advertiserIds(), scope.channelIds(), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
	}

	public DashboardViews.AccountSummary accountSummary(LocalDate startDate, LocalDate endDate, String channelId,
			String advertiserId, String appLinkId, String monitorId) {
		LocalDate today = LocalDate.now(REPORT_ZONE);
		LocalDate start = startDate == null ? today.minusDays(29) : startDate;
		LocalDate end = endDate == null ? today : endDate;
		validateDashboardRange(start, end, today);
		ScopeFilters scope = scopeFilters(channelId, advertiserId);
		Map<String, Object> values = reportMapper.accountSummary(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
				channelId, advertiserId, appLinkId, monitorId, scope.advertiserIds(), scope.channelIds());
		return new DashboardViews.AccountSummary("CNY", decimal(values, "totalBalance"),
				decimal(values, "rechargeAmount"), decimal(values, "cpcSpend"), decimal(values, "cpaSpend"),
				number(values, "rejectedCharges"), number(values, "cpaRejectedCharges"),
				LocalDateTime.now(REPORT_ZONE));
	}

	private ScopeFilters scopeFilters(String channelId, String advertiserId) {
		Snapshot snapshot = accessScope.current();
		if (StringUtils.hasText(channelId)) {
			accessScope.require(ScopeType.CHANNEL, channelId, PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(ScopeType.ADVERTISER, advertiserId, PermissionLevel.VIEW);
		}
		Restriction advertisers = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		Restriction channels = snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		return new ScopeFilters(advertisers.unrestricted() ? null : advertisers.ids(),
				channels.unrestricted() ? null : channels.ids());
	}

	private record ScopeFilters(List<String> advertiserIds, List<String> channelIds) {
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
