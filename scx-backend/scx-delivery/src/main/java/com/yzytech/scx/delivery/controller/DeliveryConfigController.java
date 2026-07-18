package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.dto.StatusChangeRequest;
import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.delivery.service.DeliveryConfigService;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/ad")
public class DeliveryConfigController {
    private final TrackingLinkMapper linkMapper;
    private final LinkEventMappingMapper mappingMapper;
    private final TrackingTaskMapper taskMapper;
    private final DeliveryConfigRevisionMapper revisionMapper;
    private final DeliveryStatusHistoryMapper statusHistoryMapper;
    private final TaskScopeService taskScopeService;
    private final DeliveryConfigService service;
    private final MediaScopeService scopeService;

    @GetMapping("/tracking-links") @SaCheckPermission("ad:link:read")
    public Result<Page<TrackingLink>> links(Page<TrackingLink> page, String mediaId, String keyword, String status,
            String partnerId, String customerId, String productId, String documentId) {
        if (!scopeService.canRead(mediaId)) return Result.fail(404, "资源不存在");
        var q = Wrappers.<TrackingLink>lambdaQuery().eq(TrackingLink::getMediaId, mediaId)
            .eq(StringUtils.hasText(status), TrackingLink::getStatus, status)
            .eq(StringUtils.hasText(partnerId), TrackingLink::getPartnerId, partnerId)
            .eq(StringUtils.hasText(customerId), TrackingLink::getCustomerId, customerId)
            .eq(StringUtils.hasText(productId), TrackingLink::getProductId, productId)
            .eq(StringUtils.hasText(documentId), TrackingLink::getDocumentId, documentId)
            .and(StringUtils.hasText(keyword), x -> x.like(TrackingLink::getCode, keyword).or().like(TrackingLink::getName, keyword));
        return Result.success(linkMapper.selectPage(page, q.orderByDesc(TrackingLink::getCreateTime)));
    }
    @GetMapping("/tracking-links/{id}") @SaCheckPermission("ad:link:read")
    public Result<TrackingLink> link(@PathVariable String id) { return call(() -> service.requireLink(id, false)); }
    @PostMapping("/tracking-links") @SaCheckPermission("ad:link:write")
    public Result<Boolean> saveLink(@Valid @RequestBody TrackingLink link) { return call(() -> service.saveLink(link)); }
    @PutMapping("/tracking-links/{id}") @SaCheckPermission("ad:link:write")
    public Result<Boolean> updateLink(@PathVariable String id, @Valid @RequestBody TrackingLink link) { link.setId(id); return call(() -> service.saveLink(link)); }
    @GetMapping("/tracking-links/{id}/event-mappings") @SaCheckPermission("ad:link:read")
    public Result<List<LinkEventMapping>> mappings(@PathVariable String id) { return call(() -> { TrackingLink link=service.requireLink(id,false); return mappingMapper.selectList(Wrappers.<LinkEventMapping>lambdaQuery().eq(LinkEventMapping::getLinkId,id).eq(LinkEventMapping::getConfigVersion,link.getConfigVersion())); }); }
    @PutMapping("/tracking-links/{id}/event-mappings") @SaCheckPermission("ad:link:write")
    public Result<Integer> mappings(@PathVariable String id, @Valid @RequestBody List<LinkEventMapping> values) { return call(() -> service.replaceMappings(id, values)); }
    @PutMapping("/tracking-links/{id}/status") @SaCheckPermission("ad:link:status")
    public Result<Boolean> linkStatus(@PathVariable String id, @Valid @RequestBody StatusChangeRequest value) { return call(() -> service.changeLinkStatus(id, value.status(), value.reason(), value.version())); }
    @GetMapping("/tracking-links/{id}/revisions") @SaCheckPermission("ad:link:read")
    public Result<List<DeliveryConfigRevision>> linkRevisions(@PathVariable String id) { return call(() -> { service.requireLink(id, false); return revisionMapper.selectList(Wrappers.<DeliveryConfigRevision>lambdaQuery().eq(DeliveryConfigRevision::getResourceType, "LINK").eq(DeliveryConfigRevision::getResourceId, id).orderByDesc(DeliveryConfigRevision::getConfigVersion)); }); }
    @GetMapping("/tracking-links/{id}/status-history") @SaCheckPermission("ad:link:read")
    public Result<List<DeliveryStatusHistory>> linkStatusHistory(@PathVariable String id) { return call(() -> { service.requireLink(id, false); return statusHistoryMapper.selectList(Wrappers.<DeliveryStatusHistory>lambdaQuery().eq(DeliveryStatusHistory::getResourceType, "LINK").eq(DeliveryStatusHistory::getResourceId, id).orderByDesc(DeliveryStatusHistory::getCreateTime)); }); }
    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        try { return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, service.resolveActiveLink(code)).build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.notFound().build(); }
    }

    @GetMapping("/tracking-tasks") @SaCheckPermission("ad:task:read")
    public Result<Page<TrackingTask>> tasks(Page<TrackingTask> page, String mediaId, String keyword, String status,
            String linkId, String channelId) {
        TaskScopeService.TaskAccess taskAccess;
        try { taskAccess = taskScopeService.accessForMedia(mediaId, false); }
        catch (IllegalArgumentException e) { return Result.fail(404, "资源不存在"); }
        var q = Wrappers.<TrackingTask>lambdaQuery().eq(TrackingTask::getMediaId, mediaId)
            .eq(StringUtils.hasText(status), TrackingTask::getStatus, status)
            .eq(StringUtils.hasText(linkId), TrackingTask::getLinkId, linkId)
            .eq(StringUtils.hasText(channelId), TrackingTask::getChannelId, channelId)
            .and(StringUtils.hasText(keyword), x -> x.like(TrackingTask::getCode, keyword).or().like(TrackingTask::getName, keyword));
        if (taskAccess.restricted()) {
            if (taskAccess.taskIds().isEmpty()) q.apply("1=0");
            else q.in(TrackingTask::getId, taskAccess.taskIds());
        }
        return Result.success(taskMapper.selectPage(page, q.orderByDesc(TrackingTask::getCreateTime)));
    }
    @GetMapping("/tracking-tasks/{id}") @SaCheckPermission("ad:task:read")
    public Result<TrackingTask> task(@PathVariable String id) { return call(() -> service.requireTask(id, false)); }
    @PostMapping("/tracking-tasks") @SaCheckPermission("ad:task:write")
    public Result<Boolean> saveTask(@Valid @RequestBody TrackingTask task) { return call(() -> service.saveTask(task)); }
    @PutMapping("/tracking-tasks/{id}") @SaCheckPermission("ad:task:write")
    public Result<Boolean> updateTask(@PathVariable String id, @Valid @RequestBody TrackingTask task) { task.setId(id); return call(() -> service.saveTask(task)); }
    @PutMapping("/tracking-tasks/{id}/status") @SaCheckPermission("ad:task:status")
    public Result<Boolean> taskStatus(@PathVariable String id, @Valid @RequestBody StatusChangeRequest value) { return call(() -> service.changeTaskStatus(id, value.status(), value.reason(), value.version())); }
    @GetMapping("/tracking-tasks/{id}/revisions") @SaCheckPermission("ad:task:read")
    public Result<List<DeliveryConfigRevision>> taskRevisions(@PathVariable String id) { return call(() -> { service.requireTask(id, false); return revisionMapper.selectList(Wrappers.<DeliveryConfigRevision>lambdaQuery().eq(DeliveryConfigRevision::getResourceType, "TASK").eq(DeliveryConfigRevision::getResourceId, id).orderByDesc(DeliveryConfigRevision::getConfigVersion)); }); }
    @GetMapping("/tracking-tasks/{id}/status-history") @SaCheckPermission("ad:task:read")
    public Result<List<DeliveryStatusHistory>> taskStatusHistory(@PathVariable String id) { return call(() -> { service.requireTask(id, false); return statusHistoryMapper.selectList(Wrappers.<DeliveryStatusHistory>lambdaQuery().eq(DeliveryStatusHistory::getResourceType, "TASK").eq(DeliveryStatusHistory::getResourceId, id).orderByDesc(DeliveryStatusHistory::getCreateTime)); }); }

    private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
        try { return Result.success(action.call()); }
        catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
        catch (Exception e) { return Result.fail("操作失败"); }
    }
}
