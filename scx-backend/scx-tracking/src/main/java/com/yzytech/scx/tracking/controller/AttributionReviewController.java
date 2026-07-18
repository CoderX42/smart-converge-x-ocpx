package com.yzytech.scx.tracking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.dto.AttributionReviewRequest;
import com.yzytech.scx.tracking.dto.AttributionReviewView;
import com.yzytech.scx.tracking.service.AttributionReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/attributions")
public class AttributionReviewController {

	private final AttributionReviewService service;

	@GetMapping
	@SaCheckPermission("ad:attribution:read")
	public Result<IPage<AttributionReviewView>> page(Page<AttributionReviewView> page, @RequestParam String mediaId,
			String reviewStatus, Boolean overLimit, String taskId,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, String callbackStatus) {
		try {
			return Result.success(
					service.page(page, mediaId, reviewStatus, overLimit, taskId, startDate, endDate, callbackStatus));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/{id}/review")
	@SaCheckPermission("ad:attribution:review")
	public Result<Boolean> review(@PathVariable String id, @RequestParam String mediaId,
			@Valid @RequestBody AttributionReviewRequest request) {
		try {
			return Result.success(service.review(id, mediaId, request));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

}
