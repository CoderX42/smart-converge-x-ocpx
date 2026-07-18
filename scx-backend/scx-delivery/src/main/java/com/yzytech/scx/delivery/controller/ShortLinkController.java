package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.dto.DpConversionRequest;
import com.yzytech.scx.delivery.dto.DpConversionResult;
import com.yzytech.scx.delivery.entity.ShortLink;
import com.yzytech.scx.delivery.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class ShortLinkController {
    private final ShortLinkService service;

    @GetMapping("/ad/short-links") @SaCheckPermission("ad:short-link:read")
    public Result<Page<ShortLink>> page(Page<ShortLink> page, @RequestParam String mediaId, String keyword, String status) {
        try { return Result.success(service.page(page, mediaId, keyword, status)); }
        catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
    }
    @PostMapping("/ad/short-links") @SaCheckPermission("ad:short-link:write")
    public Result<Boolean> save(@Valid @RequestBody ShortLink value) { return call(() -> service.save(value)); }
    @PutMapping("/ad/short-links/{id}") @SaCheckPermission("ad:short-link:write")
    public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody ShortLink value) { value.setId(id); return call(() -> service.save(value)); }
    @PostMapping("/ad/short-links/dp/convert") @SaCheckPermission("ad:short-link:write")
    public Result<DpConversionResult> convertDp(@Valid @RequestBody DpConversionRequest request) { return call(() -> service.convertDp(request)); }
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        try { return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, service.resolve(code)).build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.notFound().build(); }
    }
    private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
        try { return Result.success(action.call()); }
        catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
        catch (Exception e) { return Result.fail("短链操作失败"); }
    }
}
