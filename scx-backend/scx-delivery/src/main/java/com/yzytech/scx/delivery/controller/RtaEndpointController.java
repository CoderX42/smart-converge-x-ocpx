package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.service.RtaEndpointService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/ad/rta-endpoints")
public class RtaEndpointController {
    private final RtaEndpointService service;

    @GetMapping
    @SaCheckPermission("ad:rta:read")
    public Result<IPage<RtaEndpoint>> page(Page<RtaEndpoint> page, @RequestParam String mediaId, String status, String keyword) {
        return call(() -> service.page(page, mediaId, status, keyword));
    }

    @PostMapping
    @SaCheckPermission("ad:rta:write")
    public Result<Boolean> create(@Valid @RequestBody RtaEndpoint endpoint) {
        return call(() -> service.save(endpoint));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ad:rta:write")
    public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody RtaEndpoint endpoint) {
        endpoint.setId(id);
        return call(() -> service.save(endpoint));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("ad:rta:write")
    public Result<Boolean> changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return call(() -> service.changeStatus(id, request.getStatus(), request.getVersion()));
    }

    @PostMapping("/{id}/health-check")
    @SaCheckPermission("ad:rta:write")
    public Result<RtaEndpoint> healthCheck(@PathVariable String id) {
        return call(() -> service.healthCheck(id));
    }

    private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
        try {
            return Result.success(action.call());
        }
        catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
        catch (Exception exception) {
            return Result.fail("RTA 配置操作失败");
        }
    }

    @Data
    public static class StatusRequest {
        private String status;
        private Integer version;
    }
}
