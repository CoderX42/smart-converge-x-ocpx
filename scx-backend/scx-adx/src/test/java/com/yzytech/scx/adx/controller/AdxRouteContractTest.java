package com.yzytech.scx.adx.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.yzytech.scx.adx.dto.RoutePublishViews;

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
		assertRoute(AdxCatalogController.class, "monitors", GetMapping.class, "/monitors");
		assertRoute(AdxCatalogController.class, "monitor", GetMapping.class, "/monitors/{id}");
		assertRoute(AdxCatalogController.class, "monitorIntegration", GetMapping.class, "/monitors/{id}/integration");
		assertRoute(AdxCatalogController.class, "publishMonitor", PostMapping.class, "/monitors/{id}/publish");
		assertRoute(AdxCatalogController.class, "deductions", GetMapping.class, "/deduction-rules");
		assertRoute(AdxCatalogController.class, "reportOptions", GetMapping.class, "/report-options");

		assertRoot(AdxAdvertiserController.class, "/adx/advertisers");
		assertRoute(AdxAdvertiserController.class, "list", GetMapping.class);
		assertRoute(AdxAdvertiserController.class, "create", PostMapping.class);
		assertRoute(AdxAdvertiserController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(AdxAdvertiserController.class, "update", PutMapping.class, "/{id}");
		assertRoute(AdxAdvertiserController.class, "rotateCredential", PostMapping.class, "/{id}/credentials:rotate");
		assertRoute(AdxAdvertiserController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(AdxAdvertiserController.class, "documents", GetMapping.class, "/{id}/documents");
		assertRoute(AdxAdvertiserController.class, "uploadDocument", PostMapping.class, "/{id}/documents");
		assertRoute(AdxAdvertiserController.class, "recharge", PostMapping.class, "/{id}/recharge");
		assertRoute(AdxAdvertiserController.class, "accountLedger", GetMapping.class, "/{id}/ledger");
		assertPermission(AdxAdvertiserController.class, "rotateCredential", "adx:advertiser:credential");

		assertRoot(AdxEventController.class, "/adx");
		assertRoute(AdxEventController.class, "events", GetMapping.class, "/events");
		assertRoute(AdxEventController.class, "event", GetMapping.class, "/events/{id}");
		assertRoute(AdxEventController.class, "createEvent", PostMapping.class, "/events");
		assertRoute(AdxEventController.class, "updateEvent", PutMapping.class, "/events/{id}");
		assertRoute(AdxEventController.class, "eventStatus", PostMapping.class, "/events/{id}/status");
		assertRoute(AdxEventController.class, "eventReferences", GetMapping.class, "/events/{id}/references");
		assertRoute(AdxEventController.class, "mappings", GetMapping.class, "/event-mappings");
		assertRoute(AdxEventController.class, "mapping", GetMapping.class, "/event-mappings/{id}");
		assertRoute(AdxEventController.class, "createMapping", PostMapping.class, "/event-mappings");
		assertRoute(AdxEventController.class, "updateMapping", PutMapping.class, "/event-mappings/{id}");
		assertRoute(AdxEventController.class, "mappingStatus", PostMapping.class, "/event-mappings/{id}/status");
		assertRoute(AdxEventController.class, "mappingReferences", GetMapping.class, "/event-mappings/{id}/references");
		assertRoute(AdxEventController.class, "options", GetMapping.class, "/channels/{channelId}/event-options");
		assertPermission(AdxEventController.class, "createEvent", "adx:event:add");
		assertPermission(AdxEventController.class, "createMapping", "adx:event-mapping:add");
		assertPermission(AdxEventController.class, "options", "adx:monitor:read");

		assertRoot(AdxReportController.class, "/adx");
		assertRoute(AdxReportController.class, "dashboard", GetMapping.class, "/dashboard/summary");
		assertRoute(AdxReportController.class, "dashboardOptions", GetMapping.class, "/dashboard/options");
		assertRoute(AdxReportController.class, "accountSummary", GetMapping.class, "/dashboard/account-summary");
		assertRoute(AdxReportController.class, "summary", GetMapping.class, "/reports/hourly");
		assertRoute(AdxReportController.class, "options", GetMapping.class, "/reports/options");
		assertRoute(AdxReportController.class, "createExport", PostMapping.class, "/reports/exports");
		assertRoute(AdxReportController.class, "createDeductionExport", PostMapping.class,
				"/reports/deduction-exports");
		assertRoute(AdxReportController.class, "exportDetail", GetMapping.class, "/reports/exports/{id}");
		assertRoute(AdxReportController.class, "cancelExport", PostMapping.class, "/reports/exports/{id}/cancel");
		assertRoute(AdxReportController.class, "downloadExport", GetMapping.class, "/reports/exports/{id}/download");
		assertPermission(AdxReportController.class, "dashboard", "adx:dashboard:view");
		assertPermission(AdxReportController.class, "dashboardOptions", "adx:dashboard:view");
		assertPermission(AdxReportController.class, "accountSummary", "adx:account:read");
		assertPermission(AdxReportController.class, "summary", "adx:report:view");
		assertPermission(AdxReportController.class, "createExport", "adx:report:export");
		assertPermission(AdxReportController.class, "createDeductionExport", "adx:report:deduction:export");

		assertRoot(AdxRuntimeController.class, "/adx/runtime");
		assertRoute(AdxRuntimeController.class, "overview", GetMapping.class, "/overview");
		assertRoute(AdxRuntimeController.class, "facts", GetMapping.class, "/facts");
		assertRoute(AdxRuntimeController.class, "clicks", GetMapping.class, "/clicks");
		assertRoute(AdxRuntimeController.class, "conversions", GetMapping.class, "/conversions");
		assertRoute(AdxRuntimeController.class, "dlq", GetMapping.class, "/dlq");
		assertRoute(AdxRuntimeController.class, "execution", GetMapping.class, "/executions/{id}");
		assertRoute(AdxRuntimeController.class, "replayPreview", PostMapping.class, "/replays:preview");
		assertRoute(AdxRuntimeController.class, "replay", PostMapping.class, "/replays");
		assertRoute(AdxRuntimeController.class, "replayStatus", GetMapping.class, "/replays/{id}");
		assertRoute(AdxRuntimeController.class, "emergencyStopPreview", PostMapping.class,
				"/routes/{taskId}/emergency-stop:preview");
		assertRoute(AdxRuntimeController.class, "emergencyStop", PostMapping.class,
				"/routes/{taskId}/emergency-stop");
		assertPermission(AdxRuntimeController.class, "overview", "adx:runtime:view");
		assertPermission(AdxRuntimeController.class, "dlq", "adx:runtime:dlq:view");
		assertPermission(AdxRuntimeController.class, "replay", "adx:runtime:replay");
		assertPermission(AdxRuntimeController.class, "emergencyStop", "adx:runtime:emergency-stop");

		assertRoot(AdxTaskRuntimeController.class, "/adx/tasks");
		assertRoute(AdxTaskRuntimeController.class, "integrationFacts", GetMapping.class,
				"/{id}/integration-facts");

		assertRoot(AdxDpBlockedController.class, "/adx/tools/dp");
		assertRoute(AdxDpBlockedController.class, "convert", PostMapping.class, "/convert");
		assertPermission(AdxDpBlockedController.class, "convert", "adx:tool:dp:convert");

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

		assertRoot(AdxAccessScopeController.class, "/adx/access-scopes");
		assertRoute(AdxAccessScopeController.class, "get", GetMapping.class, "/users/{userId}");
		assertRoute(AdxAccessScopeController.class, "replace", PutMapping.class, "/users/{userId}");
		assertRoute(AdxAccessScopeController.class, "impact", GetMapping.class, "/users/{userId}/impact");
		assertRoute(AdxAccessScopeController.class, "options", GetMapping.class, "/options");
		assertPermission(AdxAccessScopeController.class, "replace", "adx:scope:edit");

		assertRoot(AdxMediaAccountController.class, "/adx/media-accounts");
		assertRoute(AdxMediaAccountController.class, "list", GetMapping.class);
		assertRoute(AdxMediaAccountController.class, "create", PostMapping.class);
		assertRoute(AdxMediaAccountController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(AdxMediaAccountController.class, "createCredential", PostMapping.class, "/{id}/credentials");
		assertRoute(AdxMediaAccountController.class, "rotateSecret", PostMapping.class,
				"/{id}/credentials/{credentialId}:rotate");
		assertRoute(AdxMediaAccountController.class, "preflight", PostMapping.class, "/{id}/connectivity:check");
		assertPermission(AdxMediaAccountController.class, "preflight", "adx:media-account:debug");

		assertRoot(AdxAppLinkController.class, "/adx/app-links");
		assertRoute(AdxAppLinkController.class, "list", GetMapping.class);
		assertRoute(AdxAppLinkController.class, "create", PostMapping.class);
		assertRoute(AdxAppLinkController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(AdxAppLinkController.class, "update", PutMapping.class, "/{id}");
		assertRoute(AdxAppLinkController.class, "completeLegacyMapping", PostMapping.class, "/{id}/mapping:complete");
		assertRoute(AdxAppLinkController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(AdxAppLinkController.class, "archive", PostMapping.class, "/{id}/archive");
		assertRoute(AdxAppLinkController.class, "restore", PostMapping.class, "/{id}/restore");
		assertRoute(AdxAppLinkController.class, "validateUrl", PostMapping.class, "/url:validate");
		assertPermission(AdxAppLinkController.class, "validateUrl", "adx:app-link:validate");

		assertRoot(AdxTaskController.class, "/adx/tasks");
		assertRoute(AdxTaskController.class, "list", GetMapping.class);
		assertRoute(AdxTaskController.class, "options", GetMapping.class, "/options");
		assertRoute(AdxTaskController.class, "detail", GetMapping.class, "/{id}");
		assertRoute(AdxTaskController.class, "create", PostMapping.class);
		assertRoute(AdxTaskController.class, "update", PutMapping.class, "/{id}");
		assertRoute(AdxTaskController.class, "eventBindings", GetMapping.class, "/{id}/event-bindings");
		assertRoute(AdxTaskController.class, "eventBindingOptions", GetMapping.class, "/{id}/event-binding-options");
		assertRoute(AdxTaskController.class, "createEventBindingOptions", GetMapping.class, "/event-binding-options");
		assertRoute(AdxTaskController.class, "eventSnapshotPreview", GetMapping.class, "/{id}/event-snapshot-preview");
		assertRoute(AdxTaskController.class, "replaceEventBindings", PutMapping.class, "/{id}/event-bindings");
		assertRoute(AdxTaskController.class, "changeStatus", PostMapping.class, "/{id}/status");
		assertRoute(AdxTaskController.class, "archive", PostMapping.class, "/{id}/archive");
		assertRoute(AdxTaskController.class, "restore", PostMapping.class, "/{id}/restore");
		assertRoute(AdxTaskController.class, "publishCheck", GetMapping.class, "/{id}/publish-check");
		assertRoute(AdxTaskController.class, "publish", PostMapping.class, "/{id}/publish");
		assertRoute(AdxTaskController.class, "publishResult", GetMapping.class,
				"/{id}/publish-requests/{publishRequestId}");
		assertRoute(AdxTaskController.class, "routeVersions", GetMapping.class, "/{id}/route-versions");
		assertRoute(AdxTaskController.class, "distribution", GetMapping.class, "/{id}/distribution");
		assertRoute(AdxTaskController.class, "copyRouteAddress", GetMapping.class, "/{id}/route-address");
		assertRoute(AdxTaskController.class, "pausePreview", PostMapping.class, "/{id}/pause:preview");
		assertRoute(AdxTaskController.class, "pause", PostMapping.class, "/{id}/pause");
		assertPermission(AdxTaskController.class, "create", "adx:task:add");
		assertPermission(AdxTaskController.class, "replaceEventBindings", "adx:task:event:edit");
		assertPermission(AdxTaskController.class, "publishCheck", "adx:task:publish");
		assertPermission(AdxTaskController.class, "copyRouteAddress", "adx:monitor:copy");
		assertPermission(AdxTaskController.class, "pause", "adx:task:status");
	}

	@Test
	void protectsEveryPublicAdxEndpointWithAPermissionCode() {
		for (Class<?> controller : Arrays.asList(AdxCatalogController.class, AdxAdvertiserController.class,
				AdxEventController.class, AdxReportController.class, AdxRuntimeController.class,
				AdxTaskRuntimeController.class, AdxDpBlockedController.class,
				ApiDebugController.class, AdxAccessScopeController.class, AdxMediaAccountController.class,
				AdxAppLinkController.class, AdxTaskController.class)) {
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
