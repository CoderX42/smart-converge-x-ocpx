package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ApiDebugRequests;
import com.yzytech.scx.adx.dto.ApiDebugViews;
import com.yzytech.scx.adx.service.ApiDebugService;
import com.yzytech.scx.common.core.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/adx/api-debug")
public class ApiDebugController {

	private final ApiDebugService service;

	public ApiDebugController(ApiDebugService service) {
		this.service = service;
	}

	@GetMapping("/targets")
	@SaCheckPermission("adx:api-debug:read")
	public Result<List<ApiDebugViews.Target>> targets(String keyword) {
		return call(() -> service.targets(keyword));
	}

	@PostMapping("/preflight")
	@SaCheckPermission("adx:api-debug:execute")
	public Result<ApiDebugViews.Preflight> preflight(@Valid @RequestBody ApiDebugRequests.Preflight request) {
		return call(() -> service.preflight(request));
	}

	@PostMapping("/authorizations")
	@SaCheckPermission("adx:api-debug:execute")
	public Result<ApiDebugViews.Authorization> authorize(@Valid @RequestBody ApiDebugRequests.Authorize request,
			HttpServletRequest servletRequest) {
		return call(() -> service.authorize(request, StpUtil.getTokenValue(), clientIp(servletRequest)));
	}

	@PostMapping("/executions")
	@SaCheckPermission("adx:api-debug:execute")
	public Result<ApiDebugViews.Execution> execute(@Valid @RequestBody ApiDebugRequests.Execute request,
			HttpServletRequest servletRequest) {
		return call(() -> service.execute(request, StpUtil.getTokenValue(), clientIp(servletRequest)));
	}

	@PostMapping(value = "/executions/multipart", consumes = "multipart/form-data")
	@SaCheckPermission("adx:api-debug:execute")
	public Result<ApiDebugViews.Execution> executeMultipart(@RequestParam String draftId,
			@RequestParam String authorizationToken, @RequestParam(required = false) List<MultipartFile> files,
			HttpServletRequest servletRequest) {
		ApiDebugRequests.Execute request = new ApiDebugRequests.Execute(draftId, authorizationToken);
		return call(() -> service.executeMultipart(request, files, StpUtil.getTokenValue(), clientIp(servletRequest)));
	}

	@GetMapping("/executions")
	@SaCheckPermission("adx:api-debug:read")
	public Result<Page<ApiDebugViews.Execution>> executions(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size) {
		return call(() -> service.executions(current, size));
	}

	@GetMapping("/executions/{id}")
	@SaCheckPermission("adx:api-debug:read")
	public Result<ApiDebugViews.Execution> execution(@PathVariable String id) {
		return call(() -> service.execution(id));
	}

	@GetMapping("/executions/{id}/logs")
	@SaCheckPermission("adx:api-debug:logs")
	public Result<List<ApiDebugViews.ExecutionLog>> logs(@PathVariable String id,
			@RequestParam(defaultValue = "0") long afterSequence) {
		return call(() -> service.logs(id, afterSequence));
	}

	@GetMapping("/logs/recent")
	@SaCheckPermission("adx:api-debug:logs-raw")
	public Result<ApiDebugViews.RecentLogs> recentLogs(@RequestParam(defaultValue = "MANAGEMENT") String source,
			String level, String logger, String keyword, @RequestParam(defaultValue = "0") long cursor,
			@RequestHeader("X-SCX-Debug-Authorization") String authorizationToken, HttpServletRequest servletRequest) {
		return call(() -> service.recentLogs(source, level, logger, keyword, cursor, authorizationToken,
				StpUtil.getTokenValue(), clientIp(servletRequest)));
	}

	@GetMapping("/cases")
	@SaCheckPermission("adx:api-debug:read")
	public Result<List<ApiDebugViews.DebugCase>> cases() {
		return call(service::cases);
	}

	@PostMapping("/cases")
	@SaCheckPermission("adx:api-debug:case:write")
	public Result<ApiDebugViews.DebugCase> createCase(@Valid @RequestBody ApiDebugRequests.CaseSave request) {
		return call(() -> service.saveCase(request));
	}

	@PutMapping("/cases/{id}")
	@SaCheckPermission("adx:api-debug:case:write")
	public Result<ApiDebugViews.DebugCase> updateCase(@PathVariable String id,
			@Valid @RequestBody ApiDebugRequests.CaseSave request) {
		ApiDebugRequests.CaseSave normalized = new ApiDebugRequests.CaseSave(id, request.caseName(),
				request.description(), request.mode(), request.scenario(), request.monitorId(), request.requestJson());
		return call(() -> service.saveCase(normalized));
	}

	@DeleteMapping("/cases/{id}")
	@SaCheckPermission("adx:api-debug:case:write")
	public Result<Boolean> deleteCase(@PathVariable String id) {
		return call(() -> {
			service.deleteCase(id);
			return true;
		});
	}

	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
	}

	private <T> Result<T> call(Supplier<T> supplier) {
		try {
			return Result.success(supplier.get());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
	}

}
