package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.EventRequests;
import com.yzytech.scx.adx.dto.EventViews;
import com.yzytech.scx.adx.service.AdxEventService;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Callable;

@RestController
@RequiredArgsConstructor
@RequestMapping("/adx")
public class AdxEventController {

	private final AdxEventService eventService;

	@GetMapping("/events")
	@SaCheckPermission("adx:event:read")
	public Result<Page<EventViews.Definition>> events(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String category, String channelId, String status,
			String keyword) {
		return call(() -> eventService.definitions(current, size, category, channelId, status, keyword));
	}

	@PostMapping("/events")
	@SaCheckPermission("adx:event:write")
	@SysLog("新增事件字典")
	public Result<EventViews.Definition> createEvent(@Valid @RequestBody EventRequests.SaveDefinition request) {
		return call(() -> eventService.saveDefinition(null, request));
	}

	@PutMapping("/events/{id}")
	@SaCheckPermission("adx:event:write")
	@SysLog("修改事件字典")
	public Result<EventViews.Definition> updateEvent(@PathVariable String id,
			@Valid @RequestBody EventRequests.SaveDefinition request) {
		return call(() -> eventService.saveDefinition(id, request));
	}

	@DeleteMapping("/events/{id}")
	@SaCheckPermission("adx:event:write")
	@SysLog("停用事件字典")
	public Result<Boolean> disableEvent(@PathVariable String id) {
		return call(() -> {
			eventService.disableDefinition(id);
			return true;
		});
	}

	@GetMapping("/event-mappings")
	@SaCheckPermission("adx:event:read")
	public Result<Page<EventViews.Mapping>> mappings(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String channelId, String status, String keyword) {
		return call(() -> eventService.mappings(current, size, channelId, status, keyword));
	}

	@PostMapping("/event-mappings")
	@SaCheckPermission("adx:event:write")
	@SysLog("新增渠道事件映射")
	public Result<EventViews.Mapping> createMapping(@Valid @RequestBody EventRequests.SaveMapping request) {
		return call(() -> eventService.saveMapping(null, request));
	}

	@PutMapping("/event-mappings/{id}")
	@SaCheckPermission("adx:event:write")
	@SysLog("修改渠道事件映射")
	public Result<EventViews.Mapping> updateMapping(@PathVariable String id,
			@Valid @RequestBody EventRequests.SaveMapping request) {
		return call(() -> eventService.saveMapping(id, request));
	}

	@DeleteMapping("/event-mappings/{id}")
	@SaCheckPermission("adx:event:write")
	@SysLog("停用渠道事件映射")
	public Result<Boolean> disableMapping(@PathVariable String id) {
		return call(() -> {
			eventService.disableMapping(id);
			return true;
		});
	}

	@GetMapping("/channels/{channelId}/event-options")
	@SaCheckPermission("adx:monitor:read")
	public Result<List<EventViews.Option>> options(@PathVariable String channelId) {
		return call(() -> eventService.options(channelId));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (BootBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
