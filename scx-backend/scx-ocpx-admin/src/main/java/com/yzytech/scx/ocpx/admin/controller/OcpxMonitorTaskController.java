package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.admin.service.OcpxMonitorTaskService;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/monitor-tasks")
public class OcpxMonitorTaskController {

	private final OcpxMonitorTaskService service;

	@GetMapping
	@SaCheckPermission("ocpx:monitor-task:view")
	public Result<IPage<MonitorTaskViews.Item>> list(Page<OcpxMonitorTask> page, String keyword, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId) {
		return Result.success(service.list(page, keyword, advertiserId, promotionLinkId, mediaId, monitorTaskId));
	}

	@GetMapping("/options")
	@SaCheckPermission(value = { "ocpx:monitor-task:view", "ocpx:report:view" }, mode = SaMode.OR)
	public Result<List<MonitorTaskViews.Option>> options(String mediaId) {
		return Result.success(service.options(mediaId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:monitor-task:view")
	public Result<MonitorTaskViews.Item> detail(@PathVariable String id) {
		return Result.success(service.detail(id));
	}

	@PostMapping
	@SaCheckPermission(value = { "ocpx:monitor-task:add", SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION })
	@SysLog("新增监测任务")
	public Result<MonitorTaskViews.Item> create(@Valid @RequestBody MonitorTaskRequests.Create request) {
		return Result.success(service.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:monitor-task:edit")
	@SysLog("修改监测任务")
	public Result<MonitorTaskViews.Item> update(@PathVariable String id,
			@Valid @RequestBody MonitorTaskRequests.Update request) {
		return Result.success(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:monitor-task:delete")
	@SysLog("删除监测任务")
	public Result<Void> delete(@PathVariable String id) {
		service.delete(id);
		return Result.success();
	}

}
