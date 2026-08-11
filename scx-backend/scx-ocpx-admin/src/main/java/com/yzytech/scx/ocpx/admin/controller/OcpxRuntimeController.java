package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.dto.RoutePublishRequests;
import com.yzytech.scx.ocpx.admin.dto.RoutePublishViews;
import com.yzytech.scx.ocpx.admin.dto.RuntimeRequests;
import com.yzytech.scx.ocpx.admin.dto.RuntimeViews;
import com.yzytech.scx.ocpx.admin.service.OcpxRuntimeService;
import com.yzytech.scx.ocpx.admin.service.OcpxTaskRoutePublicationService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.UUID;

/** S10 sanitized runtime facts, DLQ and controlled compensation API. */
@Slf4j
@RestController
@RequestMapping("/ocpx/runtime")
public class OcpxRuntimeController {

	private final OcpxRuntimeService service;

	private final OcpxTaskRoutePublicationService routePublicationService;

	public OcpxRuntimeController(OcpxRuntimeService service, OcpxTaskRoutePublicationService routePublicationService) {
		this.service = service;
		this.routePublicationService = routePublicationService;
	}

	@GetMapping("/overview")
	@SaCheckPermission("ocpx:runtime:view")
	public Result<RuntimeViews.Overview> overview() {
		return call(service::overview);
	}

	@GetMapping("/facts")
	@SaCheckPermission("ocpx:runtime:view")
	public Result<RuntimeViews.PageResult<RuntimeViews.Fact>> facts(@RequestParam(required = false) Long current,
			@RequestParam(required = false) Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime, String factType,
			String direction, String status, String advertiserId, String productId, String appLinkId, String taskId,
			String channelId, String mediaAccountId, String routeVersionId, String eventCode, String exactId,
			String traceId, String sort) {
		return call(() -> service
			.facts(query(current, size, startTime, endTime, factType, direction, status, null, advertiserId, productId,
					appLinkId, taskId, channelId, mediaAccountId, routeVersionId, eventCode, exactId, traceId, sort)));
	}

	@GetMapping("/clicks")
	@SaCheckPermission("ocpx:runtime:view")
	public Result<RuntimeViews.PageResult<RuntimeViews.Fact>> clicks(@RequestParam(required = false) Long current,
			@RequestParam(required = false) Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime, String status,
			String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, String routeVersionId, String exactId, String traceId, String sort) {
		return call(() -> service
			.facts(query(current, size, startTime, endTime, "CLICK", null, status, null, advertiserId, productId,
					appLinkId, taskId, channelId, mediaAccountId, routeVersionId, null, exactId, traceId, sort)));
	}

	@GetMapping("/conversions")
	@SaCheckPermission("ocpx:runtime:view")
	public Result<RuntimeViews.PageResult<RuntimeViews.Fact>> conversions(@RequestParam(required = false) Long current,
			@RequestParam(required = false) Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime, String status,
			String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, String routeVersionId, String eventCode, String exactId, String traceId,
			String sort) {
		return call(() -> service
			.facts(query(current, size, startTime, endTime, "CONVERSION", null, status, null, advertiserId, productId,
					appLinkId, taskId, channelId, mediaAccountId, routeVersionId, eventCode, exactId, traceId, sort)));
	}

	@GetMapping("/dlq")
	@SaCheckPermission("ocpx:runtime:dlq:view")
	public Result<RuntimeViews.PageResult<RuntimeViews.Failure>> dlq(@RequestParam(required = false) Long current,
			@RequestParam(required = false) Long size,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime, String failureClass,
			String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, String routeVersionId, String exactId, String traceId, String sort) {
		return call(() -> service
			.failures(query(current, size, startTime, endTime, null, null, null, failureClass, advertiserId, productId,
					appLinkId, taskId, channelId, mediaAccountId, routeVersionId, null, exactId, traceId, sort)));
	}

	@GetMapping("/executions/{id}")
	@SaCheckPermission("ocpx:runtime:detail")
	public Result<RuntimeViews.Timeline> execution(@PathVariable String id) {
		return call(() -> service.execution(id));
	}

	@PostMapping("/replays:preview")
	@SaCheckPermission("ocpx:runtime:replay")
	public Result<RuntimeViews.ReplayPreview> replayPreview(@Valid @RequestBody RuntimeRequests.ReplayPreview request) {
		return call(() -> service.replayPreview(request));
	}

	@PostMapping("/replays")
	@SaCheckPermission("ocpx:runtime:replay")
	public Result<RuntimeViews.ReplayStatus> replay(@Valid @RequestBody RuntimeRequests.ReplayConfirm request) {
		return call(() -> service.replay(request));
	}

	@GetMapping("/replays/{id}")
	@SaCheckPermission("ocpx:runtime:view")
	public Result<RuntimeViews.ReplayStatus> replayStatus(@PathVariable String id) {
		return call(() -> service.replayStatus(id));
	}

	@PostMapping("/routes/{taskId}/emergency-stop:preview")
	@SaCheckPermission("ocpx:runtime:emergency-stop")
	public Result<RoutePublishViews.PausePreview> emergencyStopPreview(@PathVariable String taskId,
			@Valid @RequestBody RoutePublishRequests.PausePreview request) {
		StpUtil.checkPermission("ocpx:task:status");
		StpUtil.checkPermission("ocpx:monitor:status");
		return call(() -> routePublicationService.pausePreview(taskId, request));
	}

	@PostMapping("/routes/{taskId}/emergency-stop")
	@SaCheckPermission("ocpx:runtime:emergency-stop")
	public Result<RoutePublishViews.PauseResult> emergencyStop(@PathVariable String taskId,
			@Valid @RequestBody RoutePublishRequests.Pause request) {
		StpUtil.checkPermission("ocpx:task:status");
		StpUtil.checkPermission("ocpx:monitor:status");
		return call(() -> routePublicationService.pause(taskId, request));
	}

	private RuntimeRequests.Query query(Long current, Long size, LocalDateTime startTime, LocalDateTime endTime,
			String factType, String direction, String status, String failureClass, String advertiserId,
			String productId, String appLinkId, String taskId, String channelId, String mediaAccountId,
			String routeVersionId, String eventCode, String exactId, String traceId, String sort) {
		return new RuntimeRequests.Query(current, size, startTime, endTime, factType, direction, status, failureClass,
				advertiserId, productId, appLinkId, taskId, channelId, mediaAccountId, routeVersionId, eventCode,
				exactId, traceId, sort);
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (BootBusinessException error) {
			throw error;
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		catch (Exception error) {
			String traceId = UUID.randomUUID().toString();
			log.error("OCPX runtime operation failed traceId={} errorType={}", traceId,
					error.getClass().getSimpleName(), error);
			return Result.fail("运行请求失败，请联系管理员并提供 traceId=" + traceId);
		}
	}

}
