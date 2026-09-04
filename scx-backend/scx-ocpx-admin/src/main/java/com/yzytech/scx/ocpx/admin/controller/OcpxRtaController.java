package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.RtaAdminRequests;
import com.yzytech.scx.ocpx.admin.dto.RtaAdminViews;
import com.yzytech.scx.ocpx.admin.service.OcpxRtaAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** RTA Profile、绑定、决策记录和统计管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/rta")
public class OcpxRtaController {

	private final OcpxRtaAdminService service;

	@GetMapping("/profiles")
	@SaCheckPermission("ocpx:rta:view")
	public Result<RtaAdminViews.ProfilePage> profiles(String keyword, Long current, Long size) {
		return Result.success(service.profiles(keyword, current, size));
	}

	@GetMapping("/profiles/{id}")
	@SaCheckPermission("ocpx:rta:view")
	public Result<RtaAdminViews.Profile> profile(@PathVariable String id) {
		return Result.success(service.profile(id));
	}

	@PostMapping("/profiles")
	@SaCheckPermission("ocpx:rta:add")
	public Result<RtaAdminViews.Profile> createProfile(@Valid @RequestBody RtaAdminRequests.Profile request) {
		return Result.success(service.createProfile(request));
	}

	@PutMapping("/profiles/{id}")
	@SaCheckPermission("ocpx:rta:edit")
	public Result<RtaAdminViews.Profile> updateProfile(@PathVariable String id,
			@Valid @RequestBody RtaAdminRequests.Profile request) {
		return Result.success(service.updateProfile(id, request));
	}

	@GetMapping("/monitor-tasks/{taskId}")
	@SaCheckPermission("ocpx:rta:view")
	public Result<RtaAdminViews.TaskConfig> taskConfig(@PathVariable String taskId) {
		return Result.success(service.taskConfig(taskId));
	}

	@PutMapping("/monitor-tasks/{taskId}")
	@SaCheckPermission("ocpx:rta:bind")
	public Result<RtaAdminViews.TaskConfig> updateTask(@PathVariable String taskId,
			@Valid @RequestBody RtaAdminRequests.TaskConfig request) {
		service.updateTaskConfig(taskId, request);
		return Result.success(service.taskConfig(taskId));
	}

	@GetMapping("/decisions")
	@SaCheckPermission("ocpx:rta:decision:view")
	public Result<RtaAdminViews.DecisionPage> decisions(@Valid RtaAdminRequests.Query request) {
		return Result.success(service.decisions(request));
	}

	@GetMapping("/metrics")
	@SaCheckPermission("ocpx:rta:report")
	public Result<RtaAdminViews.MetricPage> metrics(@Valid RtaAdminRequests.Query request) {
		return Result.success(service.metrics(request));
	}

}
