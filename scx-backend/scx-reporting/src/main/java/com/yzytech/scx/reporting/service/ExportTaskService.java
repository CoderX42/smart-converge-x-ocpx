package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.reporting.dto.CreateExportTaskRequest;
import com.yzytech.scx.reporting.dto.ExportArtifact;
import com.yzytech.scx.reporting.entity.ExportTask;
import com.yzytech.scx.reporting.mapper.ExportTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExportTaskService {
    private static final int MAX_DAYS = 31;
    private static final List<String> SUPPORTED_EXPORT_TYPES = List.of("DAILY_CSV", "REDUCTION_CSV");

    private final ExportTaskMapper exportTaskMapper;
    private final MediaScopeService scopeService;
    private final TaskScopeService taskScopeService;
    private final ExportTaskProcessor processor;

    public ExportTask create(CreateExportTaskRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())
                || ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("导出日期范围不能超过 31 天");
        }
        requireRead(request.getMediaId());
        String exportType = resolveExportType(request.getExportType());
        TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(request.getMediaId(), false);
        BootUser user = SecurityUtils.getUser();
        if (user == null) throw new IllegalArgumentException("登录状态已失效");
        LocalDateTime now = LocalDateTime.now();
        ExportTask task = new ExportTask();
        task.setRequesterUserId(String.valueOf(user.getUserId()));
        task.setMediaId(request.getMediaId());
        task.setExportType(exportType);
        task.setReportStartDate(request.getStartDate());
        task.setReportEndDate(request.getEndDate());
        task.setTaskScopeSnapshot(serializeTaskScope(taskAccess));
        task.setQuerySnapshotHash(SignUtils.sha256(request.getMediaId() + "|" + request.getStartDate() + "|" + request.getEndDate()
                + "|" + exportType + "|" + task.getTaskScopeSnapshot() + "|" + now));
        task.setStatus("PENDING");
        task.setRowCount(0);
        task.setRequestedAt(now);
        exportTaskMapper.insert(task);
        processor.process(task.getId());
        return task;
    }

    public IPage<ExportTask> page(Page<ExportTask> page, String mediaId, String status) {
        requireRead(mediaId);
        return exportTaskMapper.selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getMediaId, mediaId)
                .eq(status != null && !status.isBlank(), ExportTask::getStatus, status)
                .orderByDesc(ExportTask::getRequestedAt));
    }

    public ExportTask retry(String id) {
        ExportTask task = exportTaskMapper.selectById(id);
        if (task == null || !scopeService.canRead(task.getMediaId()) || !canReadSnapshot(task.getMediaId(), task.getTaskScopeSnapshot())) {
            throw new IllegalArgumentException("资源不存在");
        }
        BootUser user = SecurityUtils.getUser();
        if (user == null) throw new IllegalArgumentException("登录状态已失效");
        String userId = String.valueOf(user.getUserId());
        if (!userId.equals(task.getRequesterUserId()) && exportTaskMapper.hasAdminRole(userId) == 0) {
            throw new IllegalArgumentException("仅导出创建人或管理员可重试");
        }
        if (!"FAILED".equals(task.getStatus())) throw new IllegalArgumentException("仅失败的导出任务可重试");
        if (exportTaskMapper.resetForRetry(id, LocalDateTime.now()) != 1) {
            throw new IllegalArgumentException("导出任务状态已变更，请刷新后重试");
        }
        processor.process(id);
        return exportTaskMapper.selectById(id);
    }

    public ExportArtifact download(String id) {
        ExportTask task = exportTaskMapper.selectById(id);
        if (task == null || !scopeService.canRead(task.getMediaId())) throw new IllegalArgumentException("资源不存在");
        if (!canReadSnapshot(task.getMediaId(), task.getTaskScopeSnapshot())) throw new IllegalArgumentException("导出文件已超出当前任务数据范围");
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getExpiresAt() == null || task.getExpiresAt().isBefore(LocalDateTime.now())
                || task.getArtifactContent() == null) throw new IllegalArgumentException("导出文件不存在或已过期");
        return new ExportArtifact(task.getArtifactName(), task.getArtifactContent());
    }

    private void requireRead(String mediaId) {
        if (mediaId == null || !scopeService.canRead(mediaId)) throw new IllegalArgumentException("资源不存在");
    }

    private String resolveExportType(String exportType) {
        String value = exportType == null || exportType.isBlank() ? "DAILY_CSV" : exportType;
        if (!SUPPORTED_EXPORT_TYPES.contains(value)) {
            throw new IllegalArgumentException("不支持的导出类型");
        }
        return value;
    }

    private String serializeTaskScope(TaskScopeService.TaskAccess access) {
        return access.restricted() ? String.join(",", access.taskIds()) : "*";
    }

    private boolean canReadSnapshot(String mediaId, String snapshot) {
        TaskScopeService.TaskAccess current = taskScopeService.accessForMedia(mediaId, false);
        if ("*".equals(snapshot) || snapshot == null) return !current.restricted();
        if (!current.restricted()) return true;
        List<String> allowed = Arrays.stream(snapshot.split(",")).filter(value -> !value.isBlank()).toList();
        return current.taskIds().containsAll(allowed);
    }
}
