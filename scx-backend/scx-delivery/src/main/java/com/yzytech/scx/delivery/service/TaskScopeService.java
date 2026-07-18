package com.yzytech.scx.delivery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.entity.UserTaskScope;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.mapper.UserTaskScopeMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskScopeService {
    private final UserTaskScopeMapper mapper;
    private final TrackingTaskMapper taskMapper;
    private final MediaScopeService mediaScopeService;

    public TaskAccess accessForMedia(String mediaId, boolean write) {
        if (write ? !mediaScopeService.canWrite(mediaId) : !mediaScopeService.canRead(mediaId)) {
            throw new IllegalArgumentException("资源不存在");
        }
        if (mediaScopeService.isAdmin()) return TaskAccess.unrestricted();
        BootUser user = SecurityUtils.getUser();
        if (user == null) return TaskAccess.restricted(List.of());
        List<UserTaskScope> all = mapper.selectList(Wrappers.<UserTaskScope>lambdaQuery()
                .eq(UserTaskScope::getUserId, user.getUserId()));
        if (all.isEmpty()) return TaskAccess.unrestricted();
        return TaskAccess.restricted(all.stream().filter(item -> mediaId.equals(item.getMediaId()))
                .map(UserTaskScope::getTaskId).toList());
    }

    public boolean canRead(TrackingTask task) {
        TaskAccess access = accessForMedia(task.getMediaId(), false);
        return !access.restricted() || access.taskIds().contains(task.getId());
    }

    public boolean canWrite(TrackingTask task) {
        TaskAccess access = accessForMedia(task.getMediaId(), true);
        return !access.restricted() || access.taskIds().contains(task.getId());
    }

    public Page<UserTaskScope> page(Page<UserTaskScope> page, String mediaId, String userId) {
        accessForMedia(mediaId, false);
        return mapper.selectPage(page, Wrappers.<UserTaskScope>lambdaQuery().eq(UserTaskScope::getMediaId, mediaId)
                .eq(userId != null && !userId.isBlank(), UserTaskScope::getUserId, userId)
                .orderByDesc(UserTaskScope::getCreateTime));
    }

    @Transactional
    public boolean grant(UserTaskScope value) {
        accessForMedia(value.getMediaId(), true);
        TrackingTask task = taskMapper.selectById(value.getTaskId());
        if (task == null || !value.getMediaId().equals(task.getMediaId())) throw new IllegalArgumentException("任务不在当前媒体范围");
        if (value.getId() == null) return mapper.insert(value) == 1;
        UserTaskScope existing = mapper.selectById(value.getId());
        if (existing == null || !existing.getMediaId().equals(value.getMediaId())) throw new IllegalArgumentException("资源不存在");
        return mapper.updateById(value) == 1;
    }

    @Transactional
    public boolean revoke(String id, String mediaId) {
        accessForMedia(mediaId, true);
        UserTaskScope value = mapper.selectById(id);
        if (value == null || !mediaId.equals(value.getMediaId())) throw new IllegalArgumentException("资源不存在");
        return mapper.deleteById(id) == 1;
    }

    public record TaskAccess(boolean restricted, List<String> taskIds) {
        public static TaskAccess unrestricted() { return new TaskAccess(false, List.of()); }
        public static TaskAccess restricted(List<String> taskIds) { return new TaskAccess(true, taskIds); }
    }
}
