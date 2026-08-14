package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxAdvertiserController;
import com.yzytech.scx.ocpx.admin.controller.OcpxDeductionRuleController;
import com.yzytech.scx.ocpx.admin.controller.OcpxMediaController;
import com.yzytech.scx.ocpx.admin.controller.OcpxMonitorTaskController;
import com.yzytech.scx.ocpx.admin.controller.OcpxPromotionLinkController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxManagementCrudContractTest {

	@Test
	void allFiveManagementControllersExposePermissionProtectedCrud() {
		for (CrudContract contract : List.of(
				new CrudContract(OcpxAdvertiserController.class, "ocpx:advertiser:view", "ocpx:advertiser:add",
						"ocpx:advertiser:edit", "ocpx:advertiser:delete"),
				new CrudContract(OcpxPromotionLinkController.class, "ocpx:promotion-link:view",
						"ocpx:promotion-link:add", "ocpx:promotion-link:edit", "ocpx:promotion-link:delete"),
				new CrudContract(OcpxMediaController.class, "ocpx:media:view", "ocpx:media:add", "ocpx:media:edit",
						"ocpx:media:delete"),
				new CrudContract(OcpxMonitorTaskController.class, "ocpx:monitor-task:view", "ocpx:monitor-task:add",
						"ocpx:monitor-task:edit", "ocpx:monitor-task:delete"),
				new CrudContract(OcpxDeductionRuleController.class, "ocpx:deduction-rule:list",
						"ocpx:deduction-rule:create", "ocpx:deduction-rule:update", "ocpx:deduction-rule:delete"))) {
			assertOperation(contract.controller(), "list", GetMapping.class, contract.readPermission());
			assertOperation(contract.controller(), "detail", GetMapping.class, contract.readPermission());
			assertOperation(contract.controller(), "create", PostMapping.class, contract.createPermission());
			assertOperation(contract.controller(), "update", PutMapping.class, contract.updatePermission());
			assertOperation(contract.controller(), "delete", DeleteMapping.class, contract.deletePermission());
		}
	}

	private void assertOperation(Class<?> controller, String methodName, Class<? extends Annotation> mapping,
			String permission) {
		Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
			.filter(candidate -> methodName.equals(candidate.getName()))
			.findFirst()
			.orElseThrow();
		assertThat(method.getAnnotation(mapping)).as("%s.%s mapping", controller.getSimpleName(), methodName)
			.isNotNull();
		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
	}

	private record CrudContract(Class<?> controller, String readPermission, String createPermission,
			String updatePermission, String deletePermission) {
	}

}
