package com.yzytech.scx.adx.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AdxRouteContractTest {

	@Test
	void exposesTheCompactAdxApiContract() {
		assertRoot(AdxCatalogController.class, "/adx");
		assertRoute(AdxCatalogController.class, "channels", GetMapping.class, "/channels");
		assertRoute(AdxCatalogController.class, "createChannel", PostMapping.class, "/channels");
		assertRoute(AdxCatalogController.class, "updateChannel", PutMapping.class, "/channels/{id}");
		assertRoute(AdxCatalogController.class, "deleteChannel", DeleteMapping.class, "/channels/{id}");
		assertRoute(AdxCatalogController.class, "advertisers", GetMapping.class, "/advertisers");
		assertRoute(AdxCatalogController.class, "createAdvertiser", PostMapping.class, "/advertisers");
		assertRoute(AdxCatalogController.class, "recharge", PostMapping.class, "/advertisers/{id}/recharge");
		assertRoute(AdxCatalogController.class, "appLinks", GetMapping.class, "/app-links");
		assertRoute(AdxCatalogController.class, "monitors", GetMapping.class, "/monitors");
		assertRoute(AdxCatalogController.class, "deductions", GetMapping.class, "/deduction-rules");
		assertRoute(AdxCatalogController.class, "reportOptions", GetMapping.class, "/report-options");

		assertRoot(AdxReportController.class, "/adx");
		assertRoute(AdxReportController.class, "dashboard", GetMapping.class, "/dashboard/summary");
		assertRoute(AdxReportController.class, "summary", GetMapping.class, "/reports/hourly");
		assertRoute(AdxReportController.class, "export", GetMapping.class, "/reports/hourly/export");

		assertRoot(AdxClueController.class, "/adx/clues");
		assertRoute(AdxClueController.class, "page", GetMapping.class);
		assertRoute(AdxClueController.class, "upload", PostMapping.class, "/upload");
		assertRoute(AdxClueController.class, "batchStatus", PutMapping.class, "/batch-status");
		assertRoute(AdxClueController.class, "submit", PostMapping.class, "/submit");
		assertRoute(AdxClueController.class, "status", GetMapping.class, "/status");
	}

	@Test
	void protectsEveryPublicAdxEndpointWithAPermissionCode() {
		for (Class<?> controller : Arrays.asList(AdxCatalogController.class, AdxReportController.class,
				AdxClueController.class)) {
			for (Method method : controller.getDeclaredMethods()) {
				if (hasHttpMapping(method)) {
					SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
					assertThat(permission).as("permission on %s.%s", controller.getSimpleName(), method.getName())
						.isNotNull();
					assertThat(permission.value()).isNotEmpty();
				}
			}
		}
	}

	private void assertRoot(Class<?> controller, String route) {
		RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
		assertThat(mapping).isNotNull();
		assertThat(mapping.value()).containsExactly(route);
	}

	private void assertRoute(Class<?> controller, String methodName, Class<? extends Annotation> annotation,
			String... routes) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		Annotation mapping = method.getAnnotation(annotation);
		assertThat(mapping).as("%s on %s.%s", annotation.getSimpleName(), controller.getSimpleName(), methodName)
			.isNotNull();
		assertThat(mappingValues(mapping)).containsExactly(routes);
	}

	private String[] mappingValues(Annotation mapping) {
		if (mapping instanceof GetMapping get)
			return get.value();
		if (mapping instanceof PostMapping post)
			return post.value();
		if (mapping instanceof PutMapping put)
			return put.value();
		if (mapping instanceof DeleteMapping delete)
			return delete.value();
		throw new IllegalArgumentException("Unsupported mapping " + mapping.annotationType());
	}

	private boolean hasHttpMapping(Method method) {
		return method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)
				|| method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(DeleteMapping.class);
	}

}
