package com.yzytech.scx.ocpx.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.yzytech.scx.ocpx.admin.dto.RoutePublishViews;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class OcpxRouteContractTest {

	@Test
	void exposesTheCompactOcpxApiContract() {
		assertRoot(OcpxCatalogController.class, "/ocpx");
		assertRoute(OcpxCatalogController.class, "adapters", GetMapping.class, "/adapters");
		assertRoute(OcpxCatalogController.class, "channels", GetMapping.class, "/channels");
		assertRoute(OcpxCatalogController.class, "createChannel", PostMapping.class, "/channels");
		assertRoute(OcpxCatalogController.class, "updateChannel", PutMapping.class, "/channels/{id}");
		assertRoute(OcpxCatalogController.class, "deleteChannel", DeleteMapping.class, "/channels/{id}");
		assertRoute(OcpxCatalogController.class, "monitors", GetMapping.class, "/monitors");
		assertRoute(OcpxCatalogController.class, "monitor", GetMapping.class, "/monitors/{id}");
		assertRoute(OcpxCatalogController.class, "monitorIntegration", GetMapping.class, "/monitors/{id}/integration");
		assertRoute(OcpxCatalogController.class, "publishMonitor", PostMapping.class, "/monitors/{id}/publish");
		assertRoute(OcpxCatalogController.class, "deductions", GetMapping.class, "/deduction-rules");
		assertRoute(OcpxCatalogController.class, "reportOptions", GetMapping.class, "/report-options");

		assertRoot(OcpxAdvertiserController.class, "/ocpx/advertisers");
		assertRoute(OcpxAdvertiserController.class, "list", GetMapping.class);
		assertRoute(OcpxAdvertiserController.class, "create", PostMapping.class);
		assertRoute(OcpxAdvertiserController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(OcpxAdvertiserController.class, "update", PutMapping.class, "/{id}");
		assertRoute(OcpxAdvertiserController.class, "rotateCredential", PostMapping.class, "/{id}/credentials:rotate");
		assertRoute(OcpxAdvertiserController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(OcpxAdvertiserController.class, "documents", GetMapping.class, "/{id}/documents");
		assertRoute(OcpxAdvertiserController.class, "uploadDocument", PostMapping.class, "/{id}/documents");
		assertRoute(OcpxAdvertiserController.class, "recharge", PostMapping.class, "/{id}/recharge");
		assertRoute(OcpxAdvertiserController.class, "accountLedger", GetMapping.class, "/{id}/ledger");
		assertPermission(OcpxAdvertiserController.class, "rotateCredential", "ocpx:advertiser:credential");

		assertRoot(OcpxEventController.class, "/ocpx");
		assertRoute(OcpxEventController.class, "events", GetMapping.class, "/events");
		assertRoute(OcpxEventController.class, "event", GetMapping.class, "/events/{id}");
		assertRoute(OcpxEventController.class, "createEvent", PostMapping.class, "/events");
		assertRoute(OcpxEventController.class, "updateEvent", PutMapping.class, "/events/{id}");
		assertRoute(OcpxEventController.class, "eventStatus", PostMapping.class, "/events/{id}/status");
		assertRoute(OcpxEventController.class, "eventReferences", GetMapping.class, "/events/{id}/references");
		assertRoute(OcpxEventController.class, "mappings", GetMapping.class, "/event-mappings");
		assertRoute(OcpxEventController.class, "mapping", GetMapping.class, "/event-mappings/{id}");
		assertRoute(OcpxEventController.class, "createMapping", PostMapping.class, "/event-mappings");
		assertRoute(OcpxEventController.class, "updateMapping", PutMapping.class, "/event-mappings/{id}");
		assertRoute(OcpxEventController.class, "mappingStatus", PostMapping.class, "/event-mappings/{id}/status");
		assertRoute(OcpxEventController.class, "mappingReferences", GetMapping.class, "/event-mappings/{id}/references");
		assertRoute(OcpxEventController.class, "options", GetMapping.class, "/channels/{channelId}/event-options");
		assertPermission(OcpxEventController.class, "createEvent", "ocpx:event:add");
		assertPermission(OcpxEventController.class, "createMapping", "ocpx:event-mapping:add");
		assertPermission(OcpxEventController.class, "options", "ocpx:monitor:read");

		assertRoot(OcpxReportController.class, "/ocpx");
		assertRoute(OcpxReportController.class, "dashboard", GetMapping.class, "/dashboard/summary");
		assertRoute(OcpxReportController.class, "dashboardOptions", GetMapping.class, "/dashboard/options");
		assertRoute(OcpxReportController.class, "accountSummary", GetMapping.class, "/dashboard/account-summary");
		assertRoute(OcpxReportController.class, "summary", GetMapping.class, "/reports/hourly");
		assertRoute(OcpxReportController.class, "options", GetMapping.class, "/reports/options");
		assertRoute(OcpxReportController.class, "createExport", PostMapping.class, "/reports/exports");
		assertRoute(OcpxReportController.class, "createDeductionExport", PostMapping.class,
				"/reports/deduction-exports");
		assertRoute(OcpxReportController.class, "exportDetail", GetMapping.class, "/reports/exports/{id}");
		assertRoute(OcpxReportController.class, "cancelExport", PostMapping.class, "/reports/exports/{id}/cancel");
		assertRoute(OcpxReportController.class, "downloadExport", GetMapping.class, "/reports/exports/{id}/download");
		assertPermission(OcpxReportController.class, "dashboard", "ocpx:dashboard:view");
		assertPermission(OcpxReportController.class, "dashboardOptions", "ocpx:dashboard:view");
		assertPermission(OcpxReportController.class, "accountSummary", "ocpx:account:read");
		assertPermission(OcpxReportController.class, "summary", "ocpx:report:view");
		assertPermission(OcpxReportController.class, "createExport", "ocpx:report:export");
		assertPermission(OcpxReportController.class, "createDeductionExport", "ocpx:report:deduction:export");

		assertRoot(OcpxRuntimeController.class, "/ocpx/runtime");
		assertRoute(OcpxRuntimeController.class, "overview", GetMapping.class, "/overview");
		assertRoute(OcpxRuntimeController.class, "facts", GetMapping.class, "/facts");
		assertRoute(OcpxRuntimeController.class, "clicks", GetMapping.class, "/clicks");
		assertRoute(OcpxRuntimeController.class, "conversions", GetMapping.class, "/conversions");
		assertRoute(OcpxRuntimeController.class, "dlq", GetMapping.class, "/dlq");
		assertRoute(OcpxRuntimeController.class, "execution", GetMapping.class, "/executions/{id}");
		assertRoute(OcpxRuntimeController.class, "replayPreview", PostMapping.class, "/replays:preview");
		assertRoute(OcpxRuntimeController.class, "replay", PostMapping.class, "/replays");
		assertRoute(OcpxRuntimeController.class, "replayStatus", GetMapping.class, "/replays/{id}");
		assertRoute(OcpxRuntimeController.class, "emergencyStopPreview", PostMapping.class,
				"/routes/{taskId}/emergency-stop:preview");
		assertRoute(OcpxRuntimeController.class, "emergencyStop", PostMapping.class,
				"/routes/{taskId}/emergency-stop");
		assertPermission(OcpxRuntimeController.class, "overview", "ocpx:runtime:view");
		assertPermission(OcpxRuntimeController.class, "dlq", "ocpx:runtime:dlq:view");
		assertPermission(OcpxRuntimeController.class, "replay", "ocpx:runtime:replay");
		assertPermission(OcpxRuntimeController.class, "emergencyStop", "ocpx:runtime:emergency-stop");

		assertRoot(OcpxTaskRuntimeController.class, "/ocpx/tasks");
		assertRoute(OcpxTaskRuntimeController.class, "integrationFacts", GetMapping.class,
				"/{id}/integration-facts");

		assertRoot(OcpxDpBlockedController.class, "/ocpx/tools/dp");
		assertRoute(OcpxDpBlockedController.class, "convert", PostMapping.class, "/convert");
		assertPermission(OcpxDpBlockedController.class, "convert", "ocpx:tool:dp:convert");

		assertRoot(ApiDebugController.class, "/ocpx/api-debug");
		assertRoute(ApiDebugController.class, "targets", GetMapping.class, "/targets");
		assertRoute(ApiDebugController.class, "preflight", PostMapping.class, "/preflight");
		assertRoute(ApiDebugController.class, "authorize", PostMapping.class, "/authorizations");
		assertRoute(ApiDebugController.class, "execute", PostMapping.class, "/executions");
		assertRoute(ApiDebugController.class, "executeMultipart", PostMapping.class, "/executions/multipart");
		assertRoute(ApiDebugController.class, "execution", GetMapping.class, "/executions/{id}");
		assertRoute(ApiDebugController.class, "logs", GetMapping.class, "/executions/{id}/logs");
		assertRoute(ApiDebugController.class, "recentLogs", GetMapping.class, "/logs/recent");
		assertRoute(ApiDebugController.class, "cases", GetMapping.class, "/cases");

		assertRoot(OcpxAccessScopeController.class, "/ocpx/access-scopes");
		assertRoute(OcpxAccessScopeController.class, "get", GetMapping.class, "/users/{userId}");
		assertRoute(OcpxAccessScopeController.class, "replace", PutMapping.class, "/users/{userId}");
		assertRoute(OcpxAccessScopeController.class, "impact", GetMapping.class, "/users/{userId}/impact");
		assertRoute(OcpxAccessScopeController.class, "options", GetMapping.class, "/options");
		assertPermission(OcpxAccessScopeController.class, "replace", "ocpx:scope:edit");

		assertRoot(OcpxMediaAccountController.class, "/ocpx/media-accounts");
		assertRoute(OcpxMediaAccountController.class, "list", GetMapping.class);
		assertRoute(OcpxMediaAccountController.class, "create", PostMapping.class);
		assertRoute(OcpxMediaAccountController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(OcpxMediaAccountController.class, "createCredential", PostMapping.class, "/{id}/credentials");
		assertRoute(OcpxMediaAccountController.class, "rotateSecret", PostMapping.class,
				"/{id}/credentials/{credentialId}:rotate");
		assertRoute(OcpxMediaAccountController.class, "preflight", PostMapping.class, "/{id}/connectivity:check");
		assertPermission(OcpxMediaAccountController.class, "preflight", "ocpx:media-account:debug");

		assertRoot(OcpxAppLinkController.class, "/ocpx/app-links");
		assertRoute(OcpxAppLinkController.class, "list", GetMapping.class);
		assertRoute(OcpxAppLinkController.class, "create", PostMapping.class);
		assertRoute(OcpxAppLinkController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(OcpxAppLinkController.class, "update", PutMapping.class, "/{id}");
		assertRoute(OcpxAppLinkController.class, "completeLegacyMapping", PostMapping.class, "/{id}/mapping:complete");
		assertRoute(OcpxAppLinkController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(OcpxAppLinkController.class, "archive", PostMapping.class, "/{id}/archive");
		assertRoute(OcpxAppLinkController.class, "restore", PostMapping.class, "/{id}/restore");
		assertRoute(OcpxAppLinkController.class, "validateUrl", PostMapping.class, "/url:validate");
		assertPermission(OcpxAppLinkController.class, "validateUrl", "ocpx:app-link:validate");

		assertRoot(OcpxTaskController.class, "/ocpx/tasks");
		assertRoute(OcpxTaskController.class, "list", GetMapping.class);
		assertRoute(OcpxTaskController.class, "options", GetMapping.class, "/options");
		assertRoute(OcpxTaskController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(OcpxTaskController.class, "create", PostMapping.class);
		assertRoute(OcpxTaskController.class, "update", PutMapping.class, "/{id}");
		assertRoute(OcpxTaskController.class, "eventBindings", GetMapping.class, "/{id}/event-bindings");
		assertRoute(OcpxTaskController.class, "eventBindingOptions", GetMapping.class, "/{id}/event-binding-options");
		assertRoute(OcpxTaskController.class, "createEventBindingOptions", GetMapping.class, "/event-binding-options");
		assertRoute(OcpxTaskController.class, "eventSnapshotPreview", GetMapping.class, "/{id}/event-snapshot-preview");
		assertRoute(OcpxTaskController.class, "replaceEventBindings", PutMapping.class, "/{id}/event-bindings");
		assertRoute(OcpxTaskController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(OcpxTaskController.class, "archive", PostMapping.class, "/{id}/archive");
		assertRoute(OcpxTaskController.class, "restore", PostMapping.class, "/{id}/restore");
		assertRoute(OcpxTaskController.class, "publishCheck", GetMapping.class, "/{id}/publish-check");
		assertRoute(OcpxTaskController.class, "publish", PostMapping.class, "/{id}/publish");
		assertRoute(OcpxTaskController.class, "publishResult", GetMapping.class,
				"/{id}/publish-requests/{publishRequestId}");
		assertRoute(OcpxTaskController.class, "routeVersions", GetMapping.class, "/{id}/route-versions");
		assertRoute(OcpxTaskController.class, "distribution", GetMapping.class, "/{id}/distribution");
		assertRoute(OcpxTaskController.class, "copyRouteAddress", GetMapping.class, "/{id}/route-address");
		assertRoute(OcpxTaskController.class, "pausePreview", PostMapping.class, "/{id}/pause:preview");
		assertRoute(OcpxTaskController.class, "pause", PostMapping.class, "/{id}/pause");
		assertPermission(OcpxTaskController.class, "create", "ocpx:task:add");
		assertPermission(OcpxTaskController.class, "replaceEventBindings", "ocpx:task:event:edit");
		assertPermission(OcpxTaskController.class, "publishCheck", "ocpx:task:publish");
		assertPermission(OcpxTaskController.class, "copyRouteAddress", "ocpx:monitor:copy");
		assertPermission(OcpxTaskController.class, "pause", "ocpx:task:status");
	}

	@Test
	void protectsEveryPublicOcpxEndpointWithAPermissionCode() {
		for (Class<?> controller : Arrays.asList(OcpxCatalogController.class, OcpxAdvertiserController.class,
				OcpxEventController.class, OcpxReportController.class, OcpxRuntimeController.class,
				OcpxTaskRuntimeController.class, OcpxDpBlockedController.class,
				ApiDebugController.class, OcpxAccessScopeController.class, OcpxMediaAccountController.class,
				OcpxAppLinkController.class, OcpxTaskController.class)) {
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

	@Test
	void ordinaryPublicationResponsesDoNotExposeRouteCodesHostsSecretsOrCiphertext() {
		for (Class<?> contract : List.of(RoutePublishViews.CheckResult.class, RoutePublishViews.VersionItem.class,
				RoutePublishViews.DistributionView.class, RoutePublishViews.PublishResult.class)) {
			assertThat(Arrays.stream(contract.getRecordComponents()).map(component -> component.getName()))
				.doesNotContain("routeCode", "publicUrl", "publicTemplate", "trackingHost", "secret", "ciphertext",
						"deviceId", "callbackValue", "objectKey");
		}
	}

	private void assertRoot(Class<?> controller, String route) {
		RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
		assertThat(mapping).isNotNull();
		assertThat(mapping.value()).containsExactly(route);
	}

	private void assertPermission(Class<?> controller, String methodName, String permissionCode) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permissionCode);
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
