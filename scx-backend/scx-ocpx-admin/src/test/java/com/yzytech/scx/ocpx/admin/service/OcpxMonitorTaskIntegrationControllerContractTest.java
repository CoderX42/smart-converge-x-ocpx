package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxMonitorTaskController;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskIntegrationRequests;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxMonitorTaskIntegrationControllerContractTest {

	@Test
	void integrationEndpointsUseDedicatedPermissionAndDoNotAcceptTargetUrl() {
		Method list = method("integrationFeedbackRequests");
		Method send = method("sendIntegrationCallback");

		assertPermission(list);
		assertPermission(send);
		assertThat(list.getAnnotation(GetMapping.class).value())
			.containsExactly("/{id}/integration/feedback-requests");
		assertThat(send.getAnnotation(PostMapping.class).value())
			.containsExactly("/{id}/integration/callbacks:send");
		assertThat(Arrays.stream(MonitorTaskIntegrationRequests.Callback.class.getDeclaredFields()).map(field -> field.getName()))
			.doesNotContain("url", "callbackUrl", "targetUrl");
	}

	private Method method(String name) {
		return Arrays.stream(OcpxMonitorTaskController.class.getDeclaredMethods())
			.filter(method -> name.equals(method.getName()))
			.findFirst()
			.orElseThrow();
	}

	private void assertPermission(Method method) {
		assertThat(method.getAnnotation(SaCheckPermission.class).value())
			.containsExactly(SystemAccountConstants.MONITOR_TASK_INTEGRATION_PERMISSION);
	}

}
