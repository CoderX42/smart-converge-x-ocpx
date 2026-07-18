package com.yzytech.scx.reporting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.reporting.dto.CreateExportTaskRequest;
import com.yzytech.scx.reporting.dto.ExportArtifact;
import com.yzytech.scx.reporting.entity.ExportTask;
import com.yzytech.scx.reporting.service.ExportTaskService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/export-tasks")
public class ExportTaskController {
    private final ExportTaskService service;

    @PostMapping
    @SaCheckPermission("ad:export:create")
    public Result<ExportTask> create(@Valid @RequestBody CreateExportTaskRequest request) {
        try {
            return Result.success(service.create(request));
        }
        catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @PostMapping("/{id}/retry")
    @SaCheckPermission("ad:export:create")
    public Result<ExportTask> retry(@PathVariable String id) {
        try {
            return Result.success(service.retry(id));
        }
        catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @GetMapping
    @SaCheckPermission("ad:export:read")
    public Result<IPage<ExportTask>> page(Page<ExportTask> page, @RequestParam String mediaId, String status) {
        try {
            return Result.success(service.page(page, mediaId, status));
        }
        catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @GetMapping("/{id}/download")
    @SaCheckPermission("ad:export:read")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        ExportArtifact artifact = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(artifact.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(artifact.content());
    }
}
