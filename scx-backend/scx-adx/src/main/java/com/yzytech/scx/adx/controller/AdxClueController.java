package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.entity.AdxClue;
import com.yzytech.scx.adx.service.AdxClueService;
import com.yzytech.scx.common.core.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Callable;

@RestController
@RequiredArgsConstructor
@RequestMapping("/adx/clues")
public class AdxClueController {

	private final AdxClueService service;

	@GetMapping
	@SaCheckPermission("adx:clue:read")
	public Result<Page<AdxClue>> page(Page<AdxClue> page, String keyword, String cluePhone, Integer sourceType,
			Integer clueStatus, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return Result.success(service.page(page, keyword, cluePhone, sourceType, clueStatus, startDate, endDate));
	}

	@PostMapping
	@SaCheckPermission("adx:clue:write")
	public Result<AdxClue> create(@Valid @RequestBody CatalogRequests.SaveClue request) {
		return call(() -> service.save(null, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("adx:clue:write")
	public Result<AdxClue> update(@PathVariable String id, @Valid @RequestBody CatalogRequests.SaveClue request) {
		return call(() -> service.save(id, request));
	}

	@PostMapping("/upload")
	@SaCheckPermission("adx:clue:write")
	public Result<Integer> upload(@RequestPart("file") MultipartFile file) {
		return call(() -> service.upload(file));
	}

	@PutMapping("/batch-status")
	@SaCheckPermission("adx:clue:write")
	public Result<Integer> batchStatus(@Valid @RequestBody CatalogRequests.BatchClueStatus request) {
		return call(() -> service.batchStatus(request));
	}

	@PostMapping("/submit")
	@SaCheckPermission("adx:clue:submit")
	public Result<Map<String, Object>> submit() {
		return Result.success(service.submitPending());
	}

	@GetMapping("/status")
	@SaCheckPermission("adx:clue:read")
	public Result<Map<String, Object>> status() {
		return Result.success(service.status());
	}

	private <T> Result<T> call(Callable<T> action) {
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
