package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.entity.UserTaskScope;
import com.yzytech.scx.delivery.service.TaskScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/task-scopes")
public class TaskScopeController {
    private final TaskScopeService service;

    @GetMapping
    @SaCheckPermission("ad:task-scope:read")
    public Result<Page<UserTaskScope>> page(Page<UserTaskScope> page, @RequestParam String mediaId, String userId) {
        return call(() -> service.page(page, mediaId, userId));
    }

    @PostMapping
    @SaCheckPermission("ad:task-scope:write")
    public Result<Boolean> grant(@Valid @RequestBody UserTaskScope value) {
        return call(() -> service.grant(value));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ad:task-scope:write")
    public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody UserTaskScope value) {
        value.setId(id);
        return call(() -> service.grant(value));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ad:task-scope:write")
    public Result<Boolean> revoke(@PathVariable String id, @RequestParam String mediaId) {
        return call(() -> service.revoke(id, mediaId));
    }

    private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
        try { return Result.success(action.call()); }
        catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
        catch (Exception e) { return Result.fail("任务授权操作失败"); }
    }
}
