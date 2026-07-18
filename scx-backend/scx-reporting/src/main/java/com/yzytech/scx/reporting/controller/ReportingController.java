package com.yzytech.scx.reporting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.reporting.dto.EventDetailView;
import com.yzytech.scx.reporting.dto.ClickSensitiveDetail;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.entity.ReportDaily;
import com.yzytech.scx.reporting.entity.ReportRun;
import com.yzytech.scx.reporting.service.ReportingService;
import com.yzytech.scx.reporting.dto.ReportTrendPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController @RequiredArgsConstructor @RequestMapping("/ad/reports")
public class ReportingController {
    private final ReportingService service;
    @GetMapping("/daily") @SaCheckPermission("ad:report:read")
    public Result<IPage<ReportDaily>> daily(Page<ReportDaily> page,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mediaId, String productId, String taskId) { return call(() -> service.daily(page,date,mediaId,productId,taskId)); }
    @GetMapping("/details") @SaCheckPermission("ad:event-detail:read")
    public Result<IPage<EventDetailView>> details(Page<EventDetailView> page, String mediaId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, String eventType, String taskId,
            String eventId, String dedupStatus, String attributionStatus) {
        return call(() -> service.details(page, mediaId, date, eventType, taskId, eventId, dedupStatus, attributionStatus));
    }
    @GetMapping("/details/{rawEventId}/click-sensitive") @SaCheckPermission("ad:event-sensitive:read")
    public Result<ClickSensitiveDetail> maskedClick(@PathVariable String rawEventId, @RequestParam String mediaId) {
        return call(() -> service.maskedClick(rawEventId, mediaId));
    }
    @GetMapping("/breakdowns") @SaCheckPermission("ad:report:read")
    public Result<List<ReportBreakdown>> breakdown(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String mediaId, @RequestParam(defaultValue = "TASK") String dimension,
            String customerId, String productId, String linkId, String taskId, String channelId, String documentId) {
        return call(() -> service.breakdown(startDate, endDate, mediaId, dimension, customerId, productId, linkId, taskId,
                channelId, documentId));
    }

    @GetMapping("/overview") @SaCheckPermission("ad:report:read")
    public Result<List<ReportTrendPoint>> overview(LocalDate endDate, String mediaId) {
        try { return Result.success(service.overview(endDate, mediaId)); }
        catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
    }
    @PostMapping("/daily/{date}/refresh") @SaCheckPermission("ad:report:read")
    public Result<ReportRun> refresh(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mediaId) { return call(() -> service.refresh(date, mediaId)); }
    private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
        try {
            return Result.success(action.call());
        }
        catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            log.error("广告报表操作失败", e);
            return Result.fail("报表操作失败");
        }
    }
}
