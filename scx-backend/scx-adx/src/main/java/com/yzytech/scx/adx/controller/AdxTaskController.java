package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.TaskRequests;
import com.yzytech.scx.adx.dto.TaskViews;
import com.yzytech.scx.adx.dto.EventRequests;
import com.yzytech.scx.adx.dto.EventViews;
import com.yzytech.scx.adx.dto.RoutePublishRequests;
import com.yzytech.scx.adx.dto.RoutePublishViews;
import com.yzytech.scx.adx.entity.AdxTask;
import com.yzytech.scx.adx.service.AdxTaskService;
import com.yzytech.scx.adx.service.AdxTaskEventBindingService;
import com.yzytech.scx.adx.service.AdxTaskRoutePublicationService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/adx/tasks")
public class AdxTaskController {

	private final AdxTaskService taskService;

	private final AdxTaskEventBindingService taskEventBindingService;

	private final AdxTaskRoutePublicationService routePublicationService;

	@GetMapping
	@SaCheckPermission("adx:task:view")
	public Result<IPage<TaskViews.ListItem>> list(Page<AdxTask> page, String keyword, String advertiserId,
			String productId, String channelId, String mediaAccountId, String operatorUserId, String status,
			Boolean exposureEnabled, Boolean archived) {
		return Result.success(taskService.list(page, keyword, advertiserId, productId, channelId, mediaAccountId,
				operatorUserId, status, exposureEnabled, archived));
	}

	@GetMapping("/options")
	@SaCheckPermission("adx:task:view")
	public Result<TaskViews.Options> options(String keyword) {
		return Result.success(taskService.options(keyword));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("adx:task:view")
	public Result<TaskViews.Detail> detail(@PathVariable String id) {
		return call(() -> taskService.detail(id));
	}

	@GetMapping("/{id}/event-bindings")
	@SaCheckPermission("adx:task:view")
	public Result<EventViews.TaskBindingsResult> eventBindings(@PathVariable String id) {
		return call(() -> taskEventBindingService.bindings(id));
	}

	@GetMapping("/{id}/event-binding-options")
	@SaCheckPermission("adx:task:view")
	public Result<EventViews.TaskBindingOptions> eventBindingOptions(@PathVariable String id) {
		return call(() -> taskEventBindingService.options(id));
	}

	@GetMapping("/event-binding-options")
	@SaCheckPermission("adx:task:view")
	public Result<EventViews.TaskBindingOptions> createEventBindingOptions(String channelId, String productId,
			String mediaAccountId) {
		return call(() -> taskEventBindingService.options(channelId, productId, mediaAccountId));
	}

	@GetMapping("/{id}/event-snapshot-preview")
	@SaCheckPermission("adx:task:view")
	public Result<EventViews.SnapshotPreview> eventSnapshotPreview(@PathVariable String id) {
		return call(() -> taskEventBindingService.previewSnapshot(id));
	}

	@PostMapping
	@SaCheckPermission("adx:task:add")
	public Result<TaskViews.Detail> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody TaskRequests.Create request) {
		if (request.eventBindings() != null) {
			StpUtil.checkPermission("adx:task:event:edit");
		}
		return call(() -> taskService.create(idempotencyKey, request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("adx:task:edit")
	public Result<TaskViews.Detail> update(@PathVariable String id, @Valid @RequestBody TaskRequests.Update request) {
		if (request.eventBindings() != null) {
			StpUtil.checkPermission("adx:task:event:edit");
		}
		return call(() -> taskService.update(id, request));
	}

	@PutMapping("/{id}/event-bindings")
	@SaCheckPermission("adx:task:event:edit")
	public Result<EventViews.TaskBindingsResult> replaceEventBindings(@PathVariable String id,
			@Valid @RequestBody EventRequests.ReplaceTaskBindings request) {
		return call(() -> taskEventBindingService.replace(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("adx:task:status")
	public Result<TaskViews.Mutation> changeStatus(@PathVariable String id,
			@Valid @RequestBody TaskRequests.StatusAction request) {
		return call(() -> taskService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("adx:task:archive")
	public Result<TaskViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody TaskRequests.VersionedAction request) {
		return call(() -> taskService.archive(id, request));
	}

	@PostMapping("/{id}/restore")
	@SaCheckPermission("adx:task:archive")
	public Result<TaskViews.Mutation> restore(@PathVariable String id,
			@Valid @RequestBody TaskRequests.VersionedAction request) {
		return call(() -> taskService.restore(id, request));
	}

	@GetMapping("/{id}/publish-check")
	@SaCheckPermission("adx:task:publish")
	public Result<RoutePublishViews.CheckResult> publishCheck(@PathVariable String id) {
		StpUtil.checkPermission("adx:monitor:publish");
		return call(() -> routePublicationService.check(id));
	}

	@PostMapping("/{id}/publish")
	@SaCheckPermission("adx:task:publish")
	public Result<RoutePublishViews.PublishResult> publish(@PathVariable String id,
			@Valid @RequestBody RoutePublishRequests.Publish request) {
		StpUtil.checkPermission("adx:monitor:publish");
		return call(() -> routePublicationService.publish(id, request));
	}

	@GetMapping("/{id}/publish-requests/{publishRequestId}")
	@SaCheckPermission("adx:monitor:view")
	public Result<RoutePublishViews.PublishResult> publishResult(@PathVariable String id,
			@PathVariable String publishRequestId) {
		return call(() -> routePublicationService.publishResult(id, publishRequestId));
	}

	@GetMapping("/{id}/route-versions")
	@SaCheckPermission("adx:monitor:view")
	public Result<List<RoutePublishViews.VersionItem>> routeVersions(@PathVariable String id) {
		return call(() -> routePublicationService.versions(id));
	}

	@GetMapping("/{id}/distribution")
	@SaCheckPermission("adx:monitor:view")
	public Result<RoutePublishViews.DistributionView> distribution(@PathVariable String id) {
		return call(() -> routePublicationService.distribution(id));
	}

	@GetMapping("/{id}/route-address")
	@SaCheckPermission("adx:monitor:copy")
	public Result<RoutePublishViews.AddressView> copyRouteAddress(@PathVariable String id) {
		return call(() -> routePublicationService.copyAddress(id));
	}

	@PostMapping("/{id}/pause:preview")
	@SaCheckPermission("adx:task:status")
	public Result<RoutePublishViews.PausePreview> pausePreview(@PathVariable String id,
			@Valid @RequestBody RoutePublishRequests.PausePreview request) {
		StpUtil.checkPermission("adx:monitor:status");
		return call(() -> routePublicationService.pausePreview(id, request));
	}

	@PostMapping("/{id}/pause")
	@SaCheckPermission("adx:task:status")
	public Result<RoutePublishViews.PauseResult> pause(@PathVariable String id,
			@Valid @RequestBody RoutePublishRequests.Pause request) {
		StpUtil.checkPermission("adx:monitor:status");
		return call(() -> routePublicationService.pause(id, request));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		catch (BootBusinessException error) {
			throw error;
		}
		catch (Exception error) {
			return Result.fail("操作失败");
		}
	}

}
