package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxMonitorTaskController;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxMonitorTaskTestControllerContractTest {

	@Test
	void testEndpointsUseDedicatedPermissionAndFixedRoutes() {
		Method generate = method("generateTestLink");
		Method execute = method("executeTestLink");
		Method result = method("testResult");

		assertPermission(generate);
		assertPermission(execute);
		assertPermission(result);
		assertThat(generate.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/test-links:generate");
		assertThat(execute.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/test-links:execute");
		assertThat(result.getAnnotation(GetMapping.class).value()).containsExactly("/{id}/test-results/{requestId}");
	}

	private Method method(String name) {
		return Arrays.stream(OcpxMonitorTaskController.class.getDeclaredMethods())
			.filter(method -> name.equals(method.getName()))
			.findFirst()
			.orElseThrow();
	}

	private void assertPermission(Method method) {
		assertThat(method.getAnnotation(SaCheckPermission.class).value())
			.containsExactly(SystemAccountConstants.MONITOR_TASK_TEST_PERMISSION);
	}

}
