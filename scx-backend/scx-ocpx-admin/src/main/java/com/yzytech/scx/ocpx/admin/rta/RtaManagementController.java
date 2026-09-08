package com.yzytech.scx.ocpx.admin.rta;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ocpx/rta")
public class RtaManagementController {

	private final RtaManagement service;

	public RtaManagementController(RtaManagement service) {
		this.service = service;
	}

	@GetMapping("/options/{kind}")
	@SaCheckPermission(
			value = { "ocpx:rta:upstreams:view", "ocpx:rta:media:view", "ocpx:rta:bindings:view",
					"ocpx:rta:records:view", "ocpx:rta:logs:view", "ocpx:rta:reports:view" },
			mode = cn.dev33.satoken.annotation.SaMode.OR)
	public Result<java.util.List<Map<String, Object>>> options(@PathVariable String kind,
			@RequestParam Map<String, String> query) {
		return Result.success(service.options(kind, query));
	}

	@GetMapping("/upstreams")
	@SaCheckPermission("ocpx:rta:upstreams:view")
	public Result<RtaManagement.Page> upstreams(@RequestParam Map<String, String> query) {
		return Result.success(service.list("upstreams", query));
	}

	@GetMapping("/media")
	@SaCheckPermission("ocpx:rta:media:view")
	public Result<RtaManagement.Page> media(@RequestParam Map<String, String> query) {
		return Result.success(service.list("media", query));
	}

	@GetMapping("/bindings")
	@SaCheckPermission("ocpx:rta:bindings:view")
	public Result<RtaManagement.Page> bindings(@RequestParam Map<String, String> query) {
		return Result.success(service.list("bindings", query));
	}

	@GetMapping("/records")
	@SaCheckPermission("ocpx:rta:records:view")
	public Result<RtaManagement.Page> records(@RequestParam Map<String, String> query) {
		return Result.success(service.records(query));
	}

	@GetMapping("/logs")
	@SaCheckPermission("ocpx:rta:logs:view")
	public Result<RtaManagement.Page> logs(@RequestParam Map<String, String> query) {
		return Result.success(service.list("logs", query));
	}

	@GetMapping("/reports")
	@SaCheckPermission("ocpx:rta:reports:view")
	public Result<RtaManagement.Page> reports(@RequestParam Map<String, String> query) {
		return Result.success(service.list("reports", query));
	}

	@PostMapping("/upstreams")
	@SaCheckPermission("ocpx:rta:upstreams:edit")
	public Result<Boolean> saveUpstreams(@RequestBody Map<String, Object> body) {
		service.save("upstreams", body);
		return Result.success(true);
	}

	@PostMapping("/media")
	@SaCheckPermission("ocpx:rta:media:edit")
	public Result<Boolean> saveMedia(@RequestBody Map<String, Object> body) {
		service.save("media", body);
		return Result.success(true);
	}

	@PostMapping("/bindings")
	@SaCheckPermission("ocpx:rta:bindings:edit")
	public Result<Boolean> saveBindings(@RequestBody Map<String, Object> body) {
		service.save("bindings", body);
		return Result.success(true);
	}

}
