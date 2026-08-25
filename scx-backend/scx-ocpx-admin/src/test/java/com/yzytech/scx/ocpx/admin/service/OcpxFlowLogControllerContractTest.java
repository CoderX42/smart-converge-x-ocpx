package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxFlowLogController;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcpxFlowLogControllerContractTest {

	@Test
	void optionsUseFlowLogViewPermission() throws Exception {
		Method method = OcpxFlowLogController.class.getDeclaredMethod("options", String.class, String.class);

		assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/options");
		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly("ocpx:flow-log:view");
	}

	@Test
	void requestDetailUsesDedicatedAdministratorPermissionAndNoStoreResponse() {
		Method method = Arrays.stream(OcpxFlowLogController.class.getDeclaredMethods())
			.filter(candidate -> "requestDetail".equals(candidate.getName()))
			.findFirst()
			.orElseThrow();

		assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/{id}/request-detail");
		assertThat(method.getAnnotation(SaCheckPermission.class).value())
			.containsExactly(SystemAccountConstants.FLOW_LOG_DETAIL_PERMISSION);
		assertThat(method.getReturnType()).isEqualTo(ResponseEntity.class);

		OcpxFlowLogService service = mock(OcpxFlowLogService.class);
		when(service.requestDetail("flow-1")).thenReturn(new FlowLogViews.RequestDetail());
		ResponseEntity<?> response = new OcpxFlowLogController(service).requestDetail("flow-1");
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

}
