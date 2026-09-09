package com.yzytech.scx.ocpx.admin.rta;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/monitor-tasks/{taskId}/rta-test")
@SaCheckPermission(SystemAccountConstants.MONITOR_TASK_TEST_PERMISSION)
public class RtaTestController {

	private final RtaTestService service;

	@GetMapping
	public ResponseEntity<Result<RtaTestService.Preview>> preview(@PathVariable String taskId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(service.preview(taskId)));
	}

	@PostMapping
	public ResponseEntity<Result<Map<String, Object>>> execute(@PathVariable String taskId,
			@RequestBody RtaTestService.Input input) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(Result.success(service.execute(taskId, input)));
	}

	@GetMapping("/{requestId}")
	public ResponseEntity<Result<Map<String, Object>>> result(@PathVariable String taskId,
			@PathVariable String requestId) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(Result.success(service.result(taskId, requestId)));
	}

}
