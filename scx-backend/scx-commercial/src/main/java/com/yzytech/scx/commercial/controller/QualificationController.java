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
import org.springframework.web.bind.annotation.*;

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

	@PostMapping("/qualification-cases/{id}/documents")
	@SaCheckPermission("commercial:qualification:write")
	@Operation(summary = "上传资质文档")
	public Result<QualificationDocument> addDocument(@PathVariable String id, @RequestParam String docType,
			@RequestParam String docName, @RequestParam String fileUrl) {
		return call(() -> qualificationService.addDocument(id, docType, docName, fileUrl));
	}

	@GetMapping("/qualification-cases/{id}/documents")
	@SaCheckPermission("commercial:qualification:read")
	@Operation(summary = "查询资质文档列表")
	public Result<List<QualificationDocument>> listDocuments(@PathVariable String id) {
		return call(() -> qualificationService.listDocuments(id));
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
