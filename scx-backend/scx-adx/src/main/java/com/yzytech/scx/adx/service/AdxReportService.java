package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdxReportService {

	private static final Set<String> GRANULARITIES = Set.of("HOUR", "DATE", "MONTH", "YEAR");

	private static final String[] HEADERS = { "时间", "渠道名称", "监测链接", "广告主", "应用链接", "扣量比例", "展示数", "点击数", "点击率(%)", "消耗",
			"收入", "原始转化", "实际回传", "扣除转化", "投递失败", "激活", "唤起", "登录", "注册", "付费", "获客", "次留", "访问", "认证", "下单", "完单" };

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

	public Map<String, Object> dashboard() {
		LocalDate todayDate = LocalDate.now();
		LocalDateTime today = todayDate.atStartOfDay();
		String advertiserId = accessScope.currentAdvertiserId();
		Map<String, Object> totals = reportMapper.dashboardTotals(today.minusDays(1), today.minusDays(6),
				today.minusDays(29), today, advertiserId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("yesterdayIncome", decimal(totals, "yesterdayIncome"));
		result.put("sevenDayIncome", decimal(totals, "sevenDayIncome"));
		result.put("thirtyDayIncome", decimal(totals, "thirtyDayIncome"));
		result.put("totalIncome", decimal(totals, "totalIncome"));
		result.put("trend", reportMapper.dashboardTrend(today.minusDays(29), advertiserId));
		return result;
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

	private void writeRow(Row row, ReportRow value) {
		Object[] cells = { value.getTimeBucket(), value.getChannelName(), value.getMonitorName(),
				value.getAdvertiserName(), value.getAppLinkName(), value.getDeductionRatio(), value.getImpressions(),
				value.getClicks(), value.getClickRate(), value.getCostAmount(), value.getRevenueAmount(),
				value.getRawConversionCount(), value.getForwardedConversionCount(), value.getDeductedConversionCount(),
				value.getDeliveryFailureCount(), value.getActivateCount(), value.getEvokeCount(), value.getLoginCount(),
				value.getRegisterCount(), value.getPaymentCount(), value.getAcquisitionCount(),
				value.getNextStayCount(), value.getVisitCount(), value.getAuthenticationCount(), value.getOrderCount(),
				value.getFinishOrderCount() };
		for (int index = 0; index < cells.length; index++) {
			row.createCell(index).setCellValue(cells[index] == null ? "" : cells[index].toString());
		}
	}

}
