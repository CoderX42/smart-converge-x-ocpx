package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.dto.EventViews;
import com.yzytech.scx.ocpx.admin.service.OcpxEventModelService;
import com.yzytech.scx.ocpx.admin.service.OcpxEventService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Callable;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx")
public class OcpxEventController {

	private final OcpxEventModelService eventModelService;

	private final OcpxEventService legacyEventService;

	@GetMapping("/events")
	@SaCheckPermission("ocpx:event:view")
	public Result<IPage<EventViews.DefinitionDetail>> events(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String status, String direction, String keyword) {
		return call(() -> eventModelService.definitions(current, size, status, direction, keyword));
	}

	@GetMapping("/events/{id}")
	@SaCheckPermission("ocpx:event:view")
	public Result<EventViews.DefinitionDetail> event(@PathVariable String id) {
		return call(() -> eventModelService.definition(id));
	}

	@PostMapping("/events")
	@SaCheckPermission("ocpx:event:add")
	@SysLog("新增标准事件定义")
	public Result<EventViews.DefinitionDetail> createEvent(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody EventRequests.CreateDefinition request) {
		return call(() -> eventModelService.createDefinition(idempotencyKey, request));
	}

	@PutMapping("/events/{id}")
	@SaCheckPermission("ocpx:event:edit")
	@SysLog("修改标准事件定义")
	public Result<EventViews.DefinitionDetail> updateEvent(@PathVariable String id,
			@Valid @RequestBody EventRequests.UpdateDefinition request) {
		return call(() -> eventModelService.updateDefinition(id, request));
	}

	@PostMapping("/events/{id}/status")
	@SaCheckPermission("ocpx:event:status")
	@SysLog("变更标准事件定义状态")
	public Result<EventViews.StatusResult> eventStatus(@PathVariable String id,
			@Valid @RequestBody EventRequests.StatusAction request) {
		return call(() -> eventModelService.changeDefinitionStatus(id, request));
	}

	@GetMapping("/events/{id}/references")
	@SaCheckPermission("ocpx:event:view")
	public Result<EventViews.ReferenceSummary> eventReferences(@PathVariable String id) {
		return call(() -> eventModelService.definitionReference(id));
	}

	@GetMapping("/event-mappings")
	@SaCheckPermission("ocpx:event-mapping:view")
	public Result<IPage<EventViews.MappingDetail>> mappings(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String channelId, String status, String direction,
			String keyword) {
		return call(() -> eventModelService.mappings(current, size, channelId, status, direction, keyword));
	}

	@GetMapping("/event-mappings/{id}")
	@SaCheckPermission("ocpx:event-mapping:view")
	public Result<EventViews.MappingDetail> mapping(@PathVariable String id) {
		return call(() -> eventModelService.mapping(id));
	}

	@PostMapping("/event-mappings")
	@SaCheckPermission("ocpx:event-mapping:add")
	@SysLog("新增渠道事件映射")
	public Result<EventViews.MappingDetail> createMapping(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody EventRequests.CreateMapping request) {
		return call(() -> eventModelService.createMapping(idempotencyKey, request));
	}

	@PutMapping("/event-mappings/{id}")
	@SaCheckPermission("ocpx:event-mapping:edit")
	@SysLog("修改渠道事件映射")
	public Result<EventViews.MappingDetail> updateMapping(@PathVariable String id,
			@Valid @RequestBody EventRequests.UpdateMapping request) {
		return call(() -> eventModelService.updateMapping(id, request));
	}

	@PostMapping("/event-mappings/{id}/status")
	@SaCheckPermission("ocpx:event-mapping:status")
	@SysLog("变更渠道事件映射状态")
	public Result<EventViews.StatusResult> mappingStatus(@PathVariable String id,
			@Valid @RequestBody EventRequests.StatusAction request) {
		return call(() -> eventModelService.changeMappingStatus(id, request));
	}

	@GetMapping("/event-mappings/{id}/references")
	@SaCheckPermission("ocpx:event-mapping:view")
	public Result<EventViews.ReferenceSummary> mappingReferences(@PathVariable String id) {
		return call(() -> eventModelService.mappingReference(id));
	}

	/**
	 * V80 Monitor compatibility only. Canonical Task bindings use the scoped S6 endpoints
	 * on {@link OcpxTaskController} and never infer a mapping from this list.
	 */
	@GetMapping("/channels/{channelId}/event-options")
	@SaCheckPermission("ocpx:monitor:read")
	public Result<List<EventViews.Option>> options(@PathVariable String channelId) {
		return call(() -> legacyEventService.options(channelId));
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
