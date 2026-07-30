package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.CreateQualificationCaseRequest;
import com.yzytech.scx.commercial.dto.ReviewQualificationRequest;
import com.yzytech.scx.commercial.entity.QualificationCase;
import com.yzytech.scx.commercial.entity.QualificationDocument;
import com.yzytech.scx.commercial.entity.QualificationReviewLog;
import com.yzytech.scx.commercial.service.QualificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial")
@Tag(name = "资质审核管理", description = "资质案件创建、提交、审核与日志查询")
public class QualificationController {

	private final QualificationService qualificationService;

	@PostMapping("/qualification-cases")
	@SaCheckPermission("commercial:qualification:write")
	@Operation(summary = "创建资质案件")
	public Result<QualificationCase> create(@Valid @RequestBody CreateQualificationCaseRequest req) {
		return call(() -> qualificationService.createCase(req.orgId(), req.caseType()));
	}

	@GetMapping("/qualification-cases")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "查询资质案件列表")
	public Result<Page<QualificationCase>> list(Page<QualificationCase> page,
			@RequestParam(required = false) String orgId, @RequestParam(required = false) String status) {
		return call(() -> qualificationService.listCases(page, orgId, status));
	}

	@GetMapping("/qualification-cases/{id}")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "查询资质案件详情")
	public Result<QualificationCase> get(@PathVariable String id) {
		return call(() -> qualificationService.requireCase(id));
	}

	@PostMapping("/qualification-cases/{id}/submit")
	@SaCheckPermission("commercial:qualification:write")
	@Operation(summary = "提交资质审核")
	public Result<Boolean> submit(@PathVariable String id) {
		return call(() -> {
			qualificationService.submitCase(id);
			return true;
		});
	}

	@PostMapping("/qualification-cases/{id}/review")
	@SaCheckPermission("commercial:qualification:review")
	@Operation(summary = "审核资质案件")
	public Result<Boolean> review(@PathVariable String id, @Valid @RequestBody ReviewQualificationRequest req) {
		return call(() -> {
			qualificationService.reviewCase(id, req.decision(), req.comment(), req.expiredAt());
			return true;
		});
	}

	@PostMapping("/qualification-cases/{id}/suspend")
	@SaCheckPermission("commercial:qualification:review")
	@Operation(summary = "暂停已通过资质")
	public Result<Boolean> suspend(@PathVariable String id, @RequestParam String reason) {
		return call(() -> {
			qualificationService.suspendCase(id, reason);
			return true;
		});
	}

	@PostMapping(value = "/qualification-cases/{id}/documents", params = "fileUrl")
	@SaCheckPermission("commercial:qualification:write")
	@Operation(summary = "关联历史资质文档 URL（兼容接口）")
	public Result<QualificationDocument> addDocument(@PathVariable String id, @RequestParam String docType,
			@RequestParam String docName, @RequestParam String fileUrl) {
		return call(() -> qualificationService.addDocument(id, docType, docName, fileUrl));
	}

	@PostMapping(value = "/qualification-cases/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("commercial:qualification:write")
	@Operation(summary = "上传受控资质文档")
	public Result<QualificationDocument> uploadDocument(@PathVariable String id, @RequestParam String docType,
			@RequestPart("file") MultipartFile file) {
		return call(() -> qualificationService.uploadDocument(id, docType, file));
	}

	@GetMapping("/qualification-cases/{id}/documents")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "查询资质文档列表")
	public Result<List<QualificationDocument>> listDocuments(@PathVariable String id) {
		return call(() -> qualificationService.listDocuments(id));
	}

	@GetMapping("/qualification-documents/{documentId}/content")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "鉴权读取资质文档")
	public ResponseEntity<StreamingResponseBody> documentContent(@PathVariable String documentId) {
		try {
			QualificationService.QualificationDocumentContent content = qualificationService.openDocument(documentId);
			QualificationDocument document = content.document();
			String fileName = StringUtils.hasText(document.getOriginalFileName()) ? document.getOriginalFileName()
					: document.getDocName();
			MediaType mediaType;
			try {
				mediaType = MediaType.parseMediaType(StringUtils.hasText(document.getMimeType())
						? document.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
			}
			catch (IllegalArgumentException ignored) {
				mediaType = MediaType.APPLICATION_OCTET_STREAM;
			}
			StreamingResponseBody body = outputStream -> {
				try (var inputStream = content.stream()) {
					inputStream.transferTo(outputStream);
				}
			};
			ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(mediaType)
				.cacheControl(CacheControl.noStore().cachePrivate())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff");
			if (document.getFileSize() != null)
				response.contentLength(document.getFileSize());
			return response.body(body);
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
		}
		catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
		}
	}

	@GetMapping("/qualification-cases/{id}/review-logs")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "查询审核日志（不可变）")
	public Result<List<QualificationReviewLog>> listReviewLogs(@PathVariable String id) {
		return call(() -> qualificationService.listReviewLogs(id));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
