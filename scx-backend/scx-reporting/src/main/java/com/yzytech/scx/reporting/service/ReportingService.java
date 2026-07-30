package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.reporting.dto.AttributionResultView;
import com.yzytech.scx.reporting.dto.CallbackResultView;
import com.yzytech.scx.reporting.dto.ClickSensitiveDetail;
import com.yzytech.scx.reporting.dto.EventDetailView;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.dto.ReportTrendPoint;
import com.yzytech.scx.reporting.entity.ReportDaily;
import com.yzytech.scx.reporting.entity.ReportRun;
import com.yzytech.scx.reporting.mapper.EventDetailMapper;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.reporting.mapper.ReportHourlyMapper;
import com.yzytech.scx.reporting.mapper.ReportRunMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.entity.Product;
import com.yzytech.scx.delivery.entity.TrackingLink;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.TrackingLinkMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportingService {

	private static final Set<String> BREAKDOWN_DIMENSIONS = Set.of("DATE", "MEDIA", "MEDIA_ACCOUNT", "PARTNER",
			"CUSTOMER", "PRODUCT", "LINK", "DOCUMENT", "CHANNEL", "TASK", "CPA", "HOURLY", "RESULT", "RESULT_CB");

	private final MediaScopeService scopeService;

	private final ReportRunMapper runMapper;

	private final ReportDailyMapper dailyMapper;

	private final ReportHourlyMapper hourlyMapper;

	private final EventDetailMapper eventDetailMapper;

	private final TaskScopeService taskScopeService;

	private final TrackingTaskMapper taskMapper;

	private final ProductMapper productMapper;

	private final TrackingLinkMapper linkMapper;

	private final ObjectMapper objectMapper;

	private final ReportPipelineService pipelineService;

	public ReportRun refresh(LocalDate date, String mediaId) {
		return pipelineService.refresh(date, mediaId, "MANUAL");
	}

	public IPage<ReportDaily> daily(Page<ReportDaily> page, LocalDate date, String mediaId, String productId,
			String taskId) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		ReportRun run = runMapper.selectLatestRun(date, mediaId);
		if (run == null || !"SUCCEEDED".equals(run.getStatus()) || "SUCCEEDED_EMPTY".equals(run.getDataStatus()))
			return new Page<>(page.getCurrent(), page.getSize());
		var query = Wrappers.<ReportDaily>lambdaQuery()
			.eq(ReportDaily::getReportRunId, run.getId())
			.eq(ReportDaily::getMediaId, mediaId)
			.eq(productId != null && !productId.isBlank(), ReportDaily::getProductId, productId)
			.eq(taskId != null && !taskId.isBlank(), ReportDaily::getTaskId, taskId)
			.eq(ReportDaily::getDataOrigin, "LIVE");
		if (taskAccess.restricted()) {
			if (taskAccess.taskIds().isEmpty())
				query.apply("1=0");
			else
				query.in(ReportDaily::getTaskId, taskAccess.taskIds());
		}
		return dailyMapper.selectPage(page, query);
	}

	public IPage<EventDetailView> details(Page<EventDetailView> page, String mediaId, LocalDate date, String eventType,
			String taskId, String eventId, String dedupStatus, String attributionStatus) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		return eventDetailMapper.page(page, mediaId, date, eventType, taskId, eventId, dedupStatus, attributionStatus,
				taskAccess.restricted(), taskAccess.taskIds());
	}

	public IPage<AttributionResultView> attributionResults(Page<AttributionResultView> page, String mediaId,
			LocalDate startDate, LocalDate endDate, String eventType, String status, String taskId) {
		validateRange(startDate, endDate);
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		return eventDetailMapper.attributionResults(page, mediaId, startDate, endDate, eventType, status, taskId,
				taskAccess.restricted(), taskAccess.taskIds());
	}

	public IPage<CallbackResultView> callbackResults(Page<CallbackResultView> page, String mediaId, LocalDate startDate,
			LocalDate endDate, String status, String taskId) {
		validateRange(startDate, endDate);
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		return eventDetailMapper.callbackResults(page, mediaId, startDate, endDate, status, taskId,
				taskAccess.restricted(), taskAccess.taskIds());
	}

	public ClickSensitiveDetail maskedClick(String rawEventId, String mediaId) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		String taskId = eventDetailMapper.selectTaskId(rawEventId, mediaId);
		TrackingTask task = taskId == null ? null : taskMapper.selectById(taskId);
		if (task == null || (taskAccess.restricted() && !taskAccess.taskIds().contains(taskId)))
			throw new IllegalArgumentException("资源不存在");
		ClickSensitiveDetail detail = eventDetailMapper.selectMaskedClick(rawEventId, mediaId);
		if (detail == null)
			throw new IllegalArgumentException("资源不存在");
		detail.setAttributeKeys(parseAttributeKeys(detail.getAttributeKeysJson()));
		return detail;
	}

	private List<String> parseAttributeKeys(String value) {
		if (value == null || value.isBlank())
			return List.of();
		try {
			JsonNode keys = objectMapper.readTree(value);
			if (!keys.isArray())
				return List.of();
			List<String> result = new ArrayList<>();
			for (JsonNode key : keys) {
				if (key.isTextual() && !key.asText().isBlank()) {
					result.add(key.asText());
					if (result.size() == 50)
						break;
				}
			}
			return result;
		}
		catch (JsonProcessingException ignored) {
			return List.of();
		}
	}

	public List<ReportBreakdown> breakdown(LocalDate startDate, LocalDate endDate, String mediaId, String dimension) {
		return breakdown(startDate, endDate, mediaId, dimension, null, null, null, null, null, null);
	}

	public List<ReportBreakdown> breakdown(LocalDate startDate, LocalDate endDate, String mediaId, String dimension,
			String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		validateRange(startDate, endDate);
		String normalizedDimension = dimension == null ? "TASK" : dimension.trim().toUpperCase();
		if (!BREAKDOWN_DIMENSIONS.contains(normalizedDimension))
			throw new IllegalArgumentException("不支持的分析维度");
		List<ReportBreakdown> result = dailyMapper.selectBreakdown(mediaId, startDate, endDate, normalizedDimension,
				customerId, productId, linkId, taskId, channelId, documentId, taskAccess.restricted(),
				taskAccess.taskIds());
		return appendConfiguredZeroRows(result, startDate, endDate, mediaId, normalizedDimension, customerId, productId,
				linkId, taskId, taskAccess);
	}

	public List<ReportBreakdown> cpa(LocalDate startDate, LocalDate endDate, String mediaId, String customerId,
			String productId, String linkId, String taskId, String channelId, String documentId) {
		return breakdown(startDate, endDate, mediaId, "CPA", customerId, productId, linkId, taskId, channelId,
				documentId);
	}

	public List<ReportBreakdown> hourly(LocalDate startDate, LocalDate endDate, String mediaId, String customerId,
			String productId, String linkId, String taskId, String channelId, String documentId) {
		validateRange(startDate, endDate);
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		return hourlyMapper.selectHourly(mediaId, startDate, endDate, customerId, productId, linkId, taskId, channelId,
				documentId, taskAccess.restricted(), taskAccess.taskIds());
	}

	public List<ReportBreakdown> result(LocalDate startDate, LocalDate endDate, String mediaId, String customerId,
			String productId, String linkId, String taskId, String channelId, String documentId) {
		return breakdown(startDate, endDate, mediaId, "RESULT", customerId, productId, linkId, taskId, channelId,
				documentId);
	}

	public List<ReportBreakdown> resultCb(LocalDate startDate, LocalDate endDate, String mediaId, String customerId,
			String productId, String linkId, String taskId, String channelId, String documentId) {
		return breakdown(startDate, endDate, mediaId, "RESULT_CB", customerId, productId, linkId, taskId, channelId,
				documentId);
	}

	public List<ReportTrendPoint> overview(LocalDate endDate, String mediaId) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		LocalDate effectiveEnd = endDate == null ? LocalDate.now() : endDate;
		return dailyMapper.selectTrend(mediaId, effectiveEnd.minusDays(6), effectiveEnd, taskAccess.restricted(),
				taskAccess.taskIds());
	}

	private void validateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate))
			throw new IllegalArgumentException("请选择有效的报告日期范围");
		if (startDate.plusDays(30).isBefore(endDate))
			throw new IllegalArgumentException("单次查询最多 31 天");
	}

	private List<ReportBreakdown> appendConfiguredZeroRows(List<ReportBreakdown> rows, LocalDate startDate,
			LocalDate endDate, String mediaId, String dimension, String customerId, String productId, String linkId,
			String taskId, TaskScopeService.TaskAccess taskAccess) {
		List<ReportBreakdown> result = new ArrayList<>(rows);
		Set<String> existing = result.stream()
			.map(ReportBreakdown::getDimensionId)
			.collect(java.util.stream.Collectors.toSet());
		if ("DATE".equals(dimension)) {
			for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1))
				addZero(result, existing, date.toString(), date.toString());
		}
		else if ("MEDIA".equals(dimension)) {
			addZero(result, existing, mediaId, mediaId);
		}
		else if ("PRODUCT".equals(dimension)) {
			for (Product product : productMapper.selectList(Wrappers.<Product>lambdaQuery()
				.eq(Product::getMediaId, mediaId)
				.eq(productId != null && !productId.isBlank(), Product::getId, productId)
				.eq(customerId != null && !customerId.isBlank(), Product::getCustomerId, customerId)))
				addZero(result, existing, product.getId(), product.getName());
		}
		else if ("LINK".equals(dimension)) {
			for (TrackingLink link : linkMapper.selectList(Wrappers.<TrackingLink>lambdaQuery()
				.eq(TrackingLink::getMediaId, mediaId)
				.eq(linkId != null && !linkId.isBlank(), TrackingLink::getId, linkId)
				.eq(productId != null && !productId.isBlank(), TrackingLink::getProductId, productId)
				.eq(customerId != null && !customerId.isBlank(), TrackingLink::getCustomerId, customerId)))
				addZero(result, existing, link.getId(), link.getName());
		}
		else if ("TASK".equals(dimension)) {
			var query = Wrappers.<TrackingTask>lambdaQuery()
				.eq(TrackingTask::getMediaId, mediaId)
				.eq(taskId != null && !taskId.isBlank(), TrackingTask::getId, taskId)
				.eq(linkId != null && !linkId.isBlank(), TrackingTask::getLinkId, linkId);
			if (taskAccess.restricted()) {
				if (taskAccess.taskIds().isEmpty())
					return result;
				query.in(TrackingTask::getId, taskAccess.taskIds());
			}
			for (TrackingTask task : taskMapper.selectList(query))
				addZero(result, existing, task.getId(), task.getName());
		}
		return result;
	}

	private void addZero(List<ReportBreakdown> rows, Set<String> existing, String id, String name) {
		if (id == null || !existing.add(id))
			return;
		ReportBreakdown row = new ReportBreakdown();
		row.setDimensionId(id);
		row.setDimensionName(name);
		row.setImpressionCount(0L);
		row.setMediaImpressionCount(0L);
		row.setMediaClickCount(0L);
		row.setMediaConversionCount(0L);
		row.setUniqueClickCount(0L);
		row.setEligibleClickCount(0L);
		row.setDeduplicatedClickCount(0L);
		row.setAttributedActivateCount(0L);
		row.setAttributedRegisterCount(0L);
		row.setAttributedPayCount(0L);
		row.setDataStatus("INCOMPLETE");
		row.setQualityCode("NO_FACT");
		row.setSpendAmount(java.math.BigDecimal.ZERO);
		rows.add(row);
	}

}
