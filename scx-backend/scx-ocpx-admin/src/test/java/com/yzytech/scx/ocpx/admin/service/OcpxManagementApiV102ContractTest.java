package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.ocpx.admin.controller.OcpxAdvertiserController;
import com.yzytech.scx.ocpx.admin.controller.OcpxMediaController;
import com.yzytech.scx.ocpx.admin.controller.OcpxMonitorTaskController;
import com.yzytech.scx.ocpx.admin.controller.OcpxPromotionLinkController;
import com.yzytech.scx.ocpx.admin.controller.OcpxReportController;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxManagementApiV102ContractTest {

	private static final Set<String> RETIRED_METHODS = Set.of("changeStatus", "archive", "preflight", "connectivity",
			"validateUrl", "export");

	@Test
	void controllersDoNotExposeRetiredManagementActions() {
		for (Class<?> controller : Set.of(OcpxAdvertiserController.class, OcpxPromotionLinkController.class,
				OcpxMediaController.class, OcpxMonitorTaskController.class)) {
			Set<String> methods = Arrays.stream(controller.getDeclaredMethods())
				.map(method -> method.getName())
				.collect(Collectors.toSet());
			assertThat(methods).doesNotContainAnyElementsOf(RETIRED_METHODS);
		}
	}

	@Test
	void writeDtosContainOnlyV102Fields() {
		assertThat(componentNames(AdvertiserRequests.Create.class)).containsExactly("name", "credentialConfigKey",
				"bidUrl", "macroParams", "username", "password");
		assertThat(componentNames(MediaRequests.Create.class)).containsExactly("mediaName", "credentialConfigKey",
				"uri", "macroParams");
		assertThat(componentNames(PromotionLinkRequests.Create.class)).containsExactly("advertiserId", "linkName",
				"platform", "linkChannel", "linkUrl", "deeplink", "linkExt", "remark");
		assertThat(componentNames(MonitorTaskRequests.Create.class)).containsExactly("monitorName", "advertiserId",
				"promotionLinkId", "mediaId", "unitPrice", "bizId", "remark");
	}

	@Test
	void advertiserViewAddsOnlyNullableUsernameAndNeverReturnsAccountSecrets() {
		assertThat(componentNames(AdvertiserViews.Item.class)).contains("username")
			.doesNotContain("accountUserId", "password");
	}

	@Test
	void summaryExportUsesDedicatedPermission() throws NoSuchMethodException {
		var method = Arrays.stream(OcpxReportController.class.getDeclaredMethods())
			.filter(candidate -> "exportSummary".equals(candidate.getName()))
			.findFirst()
			.orElseThrow();

		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly("ocpx:report:export");
	}

	private String[] componentNames(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
	}

}
