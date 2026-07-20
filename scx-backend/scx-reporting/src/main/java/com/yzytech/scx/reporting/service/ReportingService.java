package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.reporting.dto.EventDetailView;
import com.yzytech.scx.reporting.dto.ClickSensitiveDetail;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.dto.ReportTrendPoint;
import com.yzytech.scx.reporting.entity.ReportDaily;
import com.yzytech.scx.reporting.entity.ReportRun;
import com.yzytech.scx.reporting.mapper.EventDetailMapper;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.reporting.mapper.ReportRunMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service @RequiredArgsConstructor
public class ReportingService {
    private static final Set<String> BREAKDOWN_DIMENSIONS = Set.of("DATE", "MEDIA", "PARTNER", "CUSTOMER", "PRODUCT", "LINK", "DOCUMENT", "CHANNEL", "TASK",
            "CPA", "HOURLY", "RESULT", "RESULT_CB");
    private final MediaScopeService scopeService;
    private final TrackingProperties trackingProperties;
    private final ReportRunMapper runMapper;
    private final ReportDailyMapper dailyMapper;
    private final EventDetailMapper eventDetailMapper;
    private final TaskScopeService taskScopeService;
    private final TrackingTaskMapper taskMapper;

    @Transactional
    public ReportRun refresh(LocalDate date, String mediaId) {
        requireRead(mediaId);
        LocalDateTime cutoff = LocalDateTime.now();
        ReportRun run = new ReportRun(); run.setReportDate(date); run.setTimezone("Asia/Shanghai");
        run.setInputCutoffReceivedAt(cutoff); run.setInputSnapshotHash(SignUtils.sha256(date + "|" + mediaId + "|" + cutoff));
        run.setRuleVersion(trackingProperties.getAttributionRuleVersion()); run.setStatus("RUNNING"); run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        dailyMapper.aggregate(run.getId(), date, mediaId, cutoff);
        run.setStatus("SUCCEEDED"); run.setFinishedAt(LocalDateTime.now()); runMapper.updateById(run);
        return run;
    }

    public IPage<ReportDaily> daily(Page<ReportDaily> page, LocalDate date, String mediaId, String productId, String taskId) {
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
        ReportRun run = runMapper.selectLatestForMedia(date, mediaId);
        if (run == null) return new Page<>(page.getCurrent(), page.getSize());
        var query = Wrappers.<ReportDaily>lambdaQuery().eq(ReportDaily::getReportRunId, run.getId())
            .eq(ReportDaily::getMediaId, mediaId).eq(productId != null && !productId.isBlank(), ReportDaily::getProductId, productId)
            .eq(taskId != null && !taskId.isBlank(), ReportDaily::getTaskId, taskId);
        if (taskAccess.restricted()) {
            if (taskAccess.taskIds().isEmpty()) query.apply("1=0");
            else query.in(ReportDaily::getTaskId, taskAccess.taskIds());
        }
        return dailyMapper.selectPage(page, query);
    }
    public IPage<EventDetailView> details(Page<EventDetailView> page, String mediaId, LocalDate date, String eventType,
            String taskId, String eventId, String dedupStatus, String attributionStatus) {
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
        return eventDetailMapper.page(page, mediaId, date, eventType, taskId, eventId, dedupStatus, attributionStatus,
                taskAccess.restricted(), taskAccess.taskIds());
    }
    public ClickSensitiveDetail maskedClick(String rawEventId, String mediaId) {
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
        String taskId = eventDetailMapper.selectTaskId(rawEventId, mediaId);
        TrackingTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null || (taskAccess.restricted() && !taskAccess.taskIds().contains(taskId))) throw new IllegalArgumentException("资源不存在");
        ClickSensitiveDetail detail = eventDetailMapper.selectMaskedClick(rawEventId, mediaId);
        if (detail == null) throw new IllegalArgumentException("资源不存在");
        return detail;
    }
    public List<ReportBreakdown> breakdown(LocalDate startDate, LocalDate endDate, String mediaId, String dimension) {
        return breakdown(startDate, endDate, mediaId, dimension, null, null, null, null, null, null);
    }

    public List<ReportBreakdown> breakdown(LocalDate startDate, LocalDate endDate, String mediaId, String dimension,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) throw new IllegalArgumentException("请选择有效的报告日期范围");
        if (startDate.plusDays(30).isBefore(endDate)) throw new IllegalArgumentException("单次多维分析最多查询 31 天");
        String normalizedDimension = dimension == null ? "TASK" : dimension.trim().toUpperCase();
        if (!BREAKDOWN_DIMENSIONS.contains(normalizedDimension)) throw new IllegalArgumentException("不支持的分析维度");
        return dailyMapper.selectBreakdown(mediaId, startDate, endDate, normalizedDimension,
                customerId, productId, linkId, taskId, channelId, documentId, taskAccess.restricted(), taskAccess.taskIds());
    }

    public List<ReportBreakdown> cpa(LocalDate startDate, LocalDate endDate, String mediaId,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        return breakdown(startDate, endDate, mediaId, "CPA", customerId, productId, linkId, taskId, channelId, documentId);
    }

    public List<ReportBreakdown> hourly(LocalDate startDate, LocalDate endDate, String mediaId,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        return breakdown(startDate, endDate, mediaId, "HOURLY", customerId, productId, linkId, taskId, channelId, documentId);
    }

    public List<ReportBreakdown> result(LocalDate startDate, LocalDate endDate, String mediaId,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        return breakdown(startDate, endDate, mediaId, "RESULT", customerId, productId, linkId, taskId, channelId, documentId);
    }

    public List<ReportBreakdown> resultCb(LocalDate startDate, LocalDate endDate, String mediaId,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        return breakdown(startDate, endDate, mediaId, "RESULT_CB", customerId, productId, linkId, taskId, channelId, documentId);
    }
    public List<ReportTrendPoint> overview(LocalDate endDate, String mediaId) {
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
        LocalDate effectiveEnd = endDate == null ? LocalDate.now() : endDate;
        return dailyMapper.selectTrend(mediaId, effectiveEnd.minusDays(6), effectiveEnd,
                taskAccess.restricted(), taskAccess.taskIds());
    }
    private void requireRead(String mediaId) { if (!scopeService.canRead(mediaId)) throw new IllegalArgumentException("资源不存在"); }
}
