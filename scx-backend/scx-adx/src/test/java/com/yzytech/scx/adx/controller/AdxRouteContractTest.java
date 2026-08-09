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
		assertRoute(AdxCatalogController.class, "adapters", GetMapping.class, "/adapters");
		assertRoute(AdxCatalogController.class, "channels", GetMapping.class, "/channels");
		assertRoute(AdxCatalogController.class, "createChannel", PostMapping.class, "/channels");
		assertRoute(AdxCatalogController.class, "updateChannel", PutMapping.class, "/channels/{id}");
		assertRoute(AdxCatalogController.class, "deleteChannel", DeleteMapping.class, "/channels/{id}");
		assertRoute(AdxCatalogController.class, "advertisers", GetMapping.class, "/advertisers");
		assertRoute(AdxCatalogController.class, "createAdvertiser", PostMapping.class, "/advertisers");
		assertRoute(AdxCatalogController.class, "recharge", PostMapping.class, "/advertisers/{id}/recharge");
		assertRoute(AdxCatalogController.class, "accountLedger", GetMapping.class, "/advertisers/{id}/ledger");
		assertRoute(AdxCatalogController.class, "appLinks", GetMapping.class, "/app-links");
		assertRoute(AdxCatalogController.class, "monitors", GetMapping.class, "/monitors");
		assertRoute(AdxCatalogController.class, "monitorIntegration", GetMapping.class, "/monitors/{id}/integration");
		assertRoute(AdxCatalogController.class, "publishMonitor", PostMapping.class, "/monitors/{id}/publish");
		assertRoute(AdxCatalogController.class, "deductions", GetMapping.class, "/deduction-rules");
		assertRoute(AdxCatalogController.class, "reportOptions", GetMapping.class, "/report-options");

		assertRoot(AdxReportController.class, "/adx");
		assertRoute(AdxReportController.class, "dashboard", GetMapping.class, "/dashboard/summary");
		assertRoute(AdxReportController.class, "summary", GetMapping.class, "/reports/hourly");
		assertRoute(AdxReportController.class, "export", GetMapping.class, "/reports/hourly/export");

		assertRoot(AdxRuntimeController.class, "/adx/runtime");
		assertRoute(AdxRuntimeController.class, "overview", GetMapping.class, "/overview");
		assertRoute(AdxRuntimeController.class, "deliveries", GetMapping.class, "/deliveries");
		assertRoute(AdxRuntimeController.class, "events", GetMapping.class, "/events");
		assertRoute(AdxRuntimeController.class, "timeline", GetMapping.class, "/events/{id}/timeline");
		assertRoute(AdxRuntimeController.class, "attempts", GetMapping.class, "/deliveries/{id}/attempts");
		assertRoute(AdxRuntimeController.class, "ingressAudits", GetMapping.class, "/ingress-audits");
		assertRoute(AdxRuntimeController.class, "replay", PostMapping.class, "/deliveries/{id}/replay");
		assertRoute(AdxRuntimeController.class, "ingressDlq", GetMapping.class, "/ingress-dlq");
		assertRoute(AdxRuntimeController.class, "replayIngressDlq", PostMapping.class, "/ingress-dlq/{id}/replay");

		assertRoot(ApiDebugController.class, "/adx/api-debug");
		assertRoute(ApiDebugController.class, "targets", GetMapping.class, "/targets");
		assertRoute(ApiDebugController.class, "preflight", PostMapping.class, "/preflight");
		assertRoute(ApiDebugController.class, "authorize", PostMapping.class, "/authorizations");
		assertRoute(ApiDebugController.class, "execute", PostMapping.class, "/executions");
		assertRoute(ApiDebugController.class, "executeMultipart", PostMapping.class, "/executions/multipart");
		assertRoute(ApiDebugController.class, "execution", GetMapping.class, "/executions/{id}");
		assertRoute(ApiDebugController.class, "logs", GetMapping.class, "/executions/{id}/logs");
		assertRoute(ApiDebugController.class, "recentLogs", GetMapping.class, "/logs/recent");
		assertRoute(ApiDebugController.class, "cases", GetMapping.class, "/cases");
	}

	@Test
	void protectsEveryPublicAdxEndpointWithAPermissionCode() {
		for (Class<?> controller : Arrays.asList(AdxCatalogController.class, AdxReportController.class,
				AdxRuntimeController.class, ApiDebugController.class)) {
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
