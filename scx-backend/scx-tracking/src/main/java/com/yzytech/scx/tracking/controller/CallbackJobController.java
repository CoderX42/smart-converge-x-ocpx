package com.yzytech.scx.tracking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.service.CallbackJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/callback-jobs")
public class CallbackJobController {

	private final CallbackJobService service;

	@GetMapping
	@SaCheckPermission("ad:event-detail:read")
	public Result<IPage<CallbackJob>> page(Page<CallbackJob> page, @RequestParam String mediaId, String status,
			String rawEventId) {
		try {
			return Result.success(service.page(page, mediaId, status, rawEventId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/stats")
	@SaCheckPermission("ad:event-detail:read")
	public Result<CallbackQueueStats> stats(@RequestParam String mediaId) {
		try {
			return Result.success(service.stats(mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@GetMapping("/outbox")
	@SaCheckPermission("ad:event-detail:read")
	public Result<IPage<com.yzytech.scx.tracking.entity.EventOutbox>> outbox(
			Page<com.yzytech.scx.tracking.entity.EventOutbox> page, @RequestParam String mediaId, String status,
			String aggregateId) {
		try {
			return Result.success(service.outboxPage(page, mediaId, status, aggregateId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/dispatch")
	@SaCheckPermission("ad:callback:replay")
	public Result<Integer> dispatch(@RequestParam String mediaId) {
		try {
			return Result.success(service.dispatchSandbox(mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/{id}/dispatch")
	@SaCheckPermission("ad:callback:replay")
	public Result<Boolean> dispatchOne(@PathVariable String id, @RequestParam String mediaId) {
		try {
			return Result.success(service.dispatchSandboxJob(id, mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/outbox/{id}/dispatch")
	@SaCheckPermission("ad:callback:replay")
	public Result<Boolean> dispatchOutboxOne(@PathVariable String id, @RequestParam String mediaId) {
		try {
			return Result.success(service.dispatchSandboxOutbox(id, mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/{id}/replay")
	@SaCheckPermission("ad:callback:replay")
	public Result<Boolean> replay(@PathVariable String id, @RequestParam String mediaId) {
		try {
			return Result.success(service.replay(id, mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/{id}/pause")
	@SaCheckPermission("ad:callback:replay")
	public Result<Boolean> pause(@PathVariable String id, @RequestParam String mediaId) {
		try {
			return Result.success(service.pause(id, mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@PostMapping("/{id}/resume")
	@SaCheckPermission("ad:callback:replay")
	public Result<Boolean> resume(@PathVariable String id, @RequestParam String mediaId) {
		try {
			return Result.success(service.resume(id, mediaId));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

}
