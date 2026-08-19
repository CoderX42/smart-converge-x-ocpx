package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxCallbackRecordController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxCallbackRecordControllerContractTest {

	@Test
	void exposesReadOnlyCallbackFactPageWithDedicatedPermission() {
		assertThat(OcpxCallbackRecordController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/ocpx/callback-records");
		Method method = Arrays.stream(OcpxCallbackRecordController.class.getDeclaredMethods())
			.filter(candidate -> "page".equals(candidate.getName()))
			.findFirst()
			.orElseThrow();
		assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly("ocpx:callback-record:view");
	}

}
