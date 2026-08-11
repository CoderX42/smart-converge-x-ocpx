package com.yzytech.scx.ocpx.admin.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.RuntimeViews;
import com.yzytech.scx.ocpx.admin.service.OcpxRuntimeService;
import com.yzytech.scx.ocpx.admin.service.OcpxTaskRoutePublicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcpxRuntimeControllerTest {

	@Test
	void unexpectedOverviewFailureReturnsTraceId() {
		OcpxRuntimeService service = mock(OcpxRuntimeService.class);
		OcpxTaskRoutePublicationService routePublicationService = mock(OcpxTaskRoutePublicationService.class);
		when(service.overview()).thenThrow(new IllegalStateException("database unavailable"));
		OcpxRuntimeController controller = new OcpxRuntimeController(service, routePublicationService);

		Result<RuntimeViews.Overview> result = controller.overview();

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getData()).isNull();
		assertThat(result.getMsg())
			.startsWith("运行请求失败，请联系管理员并提供 traceId=")
			.matches(".*traceId=[0-9a-f-]{36}$");
		assertThat(result.getMsg()).doesNotContain("database unavailable");
	}

}
