package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.admin.service.OcpxMonitorTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/monitor-tasks")
public class OcpxMonitorTaskController {

	private final OcpxMonitorTaskService service;

	@GetMapping
	@SaCheckPermission("ocpx:monitor-task:view")
	public Result<IPage<MonitorTaskViews.Item>> list(Page<OcpxMonitorTask> page, String keyword,
			String advertiserId, String promotionLinkId, String mediaId, String status) {
		return Result.success(service.list(page, keyword, advertiserId, promotionLinkId, mediaId, status));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:monitor-task:view")
	public Result<MonitorTaskViews.Item> detail(@PathVariable String id) {
		return call(() -> service.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:monitor-task:add")
	@SysLog("新增监测任务")
	public Result<MonitorTaskViews.Item> create(@Valid @RequestBody MonitorTaskRequests.Create request) {
		return call(() -> service.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:monitor-task:edit")
	@SysLog("修改监测任务")
	public Result<MonitorTaskViews.Item> update(@PathVariable String id,
			@Valid @RequestBody MonitorTaskRequests.Update request) {
		return call(() -> service.update(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:monitor-task:status")
	@SysLog("修改监测任务状态")
	public Result<MonitorTaskViews.Mutation> status(@PathVariable String id,
			@Valid @RequestBody MonitorTaskRequests.ChangeStatus request) {
		return call(() -> service.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:monitor-task:archive")
	@SysLog("归档监测任务")
	public Result<MonitorTaskViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody MonitorTaskRequests.VersionedAction request) {
		return call(() -> service.archive(id, request));
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
