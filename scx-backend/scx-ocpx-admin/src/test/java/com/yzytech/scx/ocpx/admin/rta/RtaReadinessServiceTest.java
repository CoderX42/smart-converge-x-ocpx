package com.yzytech.scx.ocpx.admin.rta;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.admin.rta.RtaReadinessService.Level;
import com.yzytech.scx.ocpx.admin.rta.RtaReadinessService.Status;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RtaReadinessServiceTest {

	RtaManagementSqlMapper mapper;

	AdvertiserScope scope;

	RtaReadinessService service;

	@BeforeEach
	void setup() {
		mapper = mock(RtaManagementSqlMapper.class);
		scope = mock(AdvertiserScope.class);
		service = new RtaReadinessService(mapper, scope, "https://scx.example.com/ocpx",
				List.of("rta.adapi.cn", "media.example.com", "upstream.example.com"));
	}

	static Map<String, Object> genericRoute(String taskId) {
		Map<String, Object> row = new HashMap<>();
		row.put("task_id", taskId);
		row.put("task_name", "Task " + taskId);
		row.put("rta_enabled", 1);
		row.put("advertiser_id", "a1");
		row.put("advertiser_name", "Adv");
		row.put("media_id", "m1");
		row.put("media_name", "Media");
		row.put("link_url", "https://link.example.com/click");
		row.put("binding_task_id", taskId);
		row.put("binding_enabled", 1);
		row.put("upstream_id", "u1");
		row.put("product_id", "p1");
		row.put("external_task_id", "e1");
		row.put("strategy_id", 7);
		row.put("oppo_pkg", "");
		row.put("access_media_id", "m1");
		row.put("media_enabled", 1);
		row.put("media_protocol", "GENERIC_ASYNC");
		row.put("notify_url", "https://media.example.com/notify");
		row.put("upstream_ref_id", "u1");
		row.put("upstream_name", "Upstream");
		row.put("upstream_enabled", 1);
		row.put("upstream_protocol", "GENERIC_ASYNC");
		row.put("upstream_endpoint", "https://upstream.example.com/rta");
		row.put("upstream_config", "{}");
		return row;
	}

	static Map<String, Object> oppoWeifengRoute(String taskId) {
		var row = genericRoute(taskId);
		row.put("media_protocol", "OPPO");
		row.put("notify_url", "");
		row.put("upstream_protocol", "WEIFENG_RTA");
		row.put("upstream_endpoint", "https://rta.adapi.cn/rtaCommon/sendRta");
		row.put("upstream_config", "{\"aid\":\"5103\",\"did\":\"zsyd\"}");
		row.put("link_url", "https://ad.adapi.cn/click?aid=5103&did=zsyd");
		row.put("product_id", null);
		row.put("external_task_id", null);
		row.put("oppo_pkg", "com.example.app");
		return row;
	}

	static Set<String> codes(RtaReadinessService.Route route, Level level) {
		return route.checks()
			.stream()
			.filter(c -> c.level() == level)
			.map(RtaReadinessService.Check::code)
			.collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void completeGenericRouteIsReady() {
		var route = service.evaluate(genericRoute("t1"));
		assertThat(route.status()).isEqualTo(Status.READY);
		assertThat(codes(route, Level.BLOCK)).isEmpty();
		assertThat(codes(route, Level.WARN)).isEmpty();
		assertThat(route.media().state()).isEqualTo("ENABLED");
	}

	@Test
	void completeOppoWeifengRouteIsReady() {
		var route = service.evaluate(oppoWeifengRoute("t1"));
		assertThat(route.status()).isEqualTo(Status.READY);
		assertThat(codes(route, Level.BLOCK)).isEmpty();
		assertThat(codes(route, Level.WARN)).isEmpty();
		assertThat(route.strategyId()).isEqualTo(7);
	}

	@Test
	void missingBindingIsNotConfiguredAndPointsToBinding() {
		var row = genericRoute("t1");
		for (String key : List.of("binding_task_id", "binding_enabled", "upstream_id", "upstream_ref_id",
				"upstream_enabled", "upstream_protocol"))
			row.remove(key);
		var route = service.evaluate(row);
		assertThat(route.status()).isEqualTo(Status.NOT_CONFIGURED);
		assertThat(route.checks()).anySatisfy(c -> {
			assertThat(c.code()).isEqualTo("BINDING_MISSING");
			assertThat(c.fixTarget()).isEqualTo("binding");
		});
	}

	@Test
	void disabledBindingOrTaskSwitchIsDisabled() {
		var row = genericRoute("t1");
		row.put("binding_enabled", 0);
		assertThat(service.evaluate(row).status()).isEqualTo(Status.DISABLED);
		row = genericRoute("t1");
		row.put("rta_enabled", 0);
		assertThat(service.evaluate(row).status()).isEqualTo(Status.DISABLED);
	}

	@Test
	void missingOrDisabledMediaAccessBlocks() {
		var row = genericRoute("t1");
		row.put("access_media_id", null);
		var route = service.evaluate(row);
		assertThat(route.status()).isEqualTo(Status.INCOMPLETE);
		assertThat(codes(route, Level.BLOCK)).contains("MEDIA_ACCESS_MISSING");
		row = genericRoute("t1");
		row.put("media_enabled", 0);
		assertThat(codes(service.evaluate(row), Level.BLOCK)).contains("MEDIA_ACCESS_DISABLED");
	}

	@Test
	void missingOrDisabledUpstreamBlocks() {
		var row = genericRoute("t1");
		row.put("upstream_ref_id", null);
		assertThat(codes(service.evaluate(row), Level.BLOCK)).contains("UPSTREAM_MISSING");
		row = genericRoute("t1");
		row.put("upstream_enabled", 0);
		var route = service.evaluate(row);
		assertThat(codes(route, Level.BLOCK)).contains("UPSTREAM_DISABLED");
		assertThat(route.status()).isEqualTo(Status.INCOMPLETE);
	}

	@Test
	void oppoMediaWithGenericUpstreamIsIncompatible() {
		var row = genericRoute("t1");
		row.put("media_protocol", "OPPO");
		row.put("oppo_pkg", "com.example.app");
		assertThat(codes(service.evaluate(row), Level.BLOCK)).contains("PROTOCOL_INCOMPATIBLE");
	}

	@Test
	void genericMappingMustBeComplete() {
		var row = genericRoute("t1");
		row.put("external_task_id", " ");
		assertThat(codes(service.evaluate(row), Level.BLOCK)).contains("MAPPING_MISSING");
	}

	@Test
	void weifengPlanMismatchBlocksAndPointsToUpstream() {
		var row = oppoWeifengRoute("t1");
		row.put("link_url", "https://ad.adapi.cn/click?aid=9999&did=zsyd");
		var route = service.evaluate(row);
		assertThat(route.checks()).anySatisfy(c -> {
			assertThat(c.code()).isEqualTo("ROUTE_IDENTITY_MISMATCH");
			assertThat(c.level()).isEqualTo(Level.BLOCK);
			assertThat(c.fixTarget()).isEqualTo("upstream");
		});
	}

	@Test
	void hostsAndOppoPackageOnlyWarn() {
		var row = oppoWeifengRoute("t1");
		row.put("oppo_pkg", "");
		var route = service.evaluate(row);
		assertThat(route.status()).isEqualTo(Status.READY);
		assertThat(codes(route, Level.WARN)).contains("OPPO_PKG_MISSING");
		row = genericRoute("t1");
		row.put("notify_url", "https://other.example.com/notify");
		row.put("upstream_endpoint", "https://other.example.com/rta");
		route = service.evaluate(row);
		assertThat(route.status()).isEqualTo(Status.READY);
		assertThat(codes(route, Level.WARN)).contains("NOTIFY_HOST_NOT_ALLOWED", "UPSTREAM_HOST_NOT_ALLOWED");
	}

	@Test
	void placeholderPublicBaseUrlProducesNotice() {
		var placeholder = new RtaReadinessService(mapper, scope, "https://example.invalid/ocpx", List.of());
		assertThat(placeholder.notices()).extracting(RtaReadinessService.Check::code)
			.contains("PUBLIC_BASE_URL_PLACEHOLDER", "ENGINE_CONFIG_UNVERIFIED");
		assertThat(service.notices()).extracting(RtaReadinessService.Check::code)
			.containsExactly("ENGINE_CONFIG_UNVERIFIED");
	}

	@Test
	void overviewSummarizesFiltersIssuesAndAttachesStats() {
		var broken = genericRoute("t2");
		broken.put("media_enabled", 0);
		var disabled = genericRoute("t3");
		disabled.put("binding_enabled", 0);
		when(scope.constrain(any())).thenReturn(null);
		when(mapper.selectReadinessRoutes(isNull(), isNull(), isNull(), isNull()))
			.thenReturn(List.of(genericRoute("t1"), broken, disabled));
		when(mapper.selectRecentTaskStats(any(), anyList()))
			.thenReturn(List.of(Map.of("task_id", "t2", "request_count", 10L, "allow_count", 4L)));

		var all = service.overview(Map.of());
		assertThat(all.total()).isEqualTo(3);
		assertThat(all.summary()).isEqualTo(new RtaReadinessService.Summary(1, 1, 1, 0));

		var issues = service.overview(Map.of("onlyIssues", "true"));
		assertThat(issues.records()).extracting(RtaReadinessService.Route::monitorTaskId).containsExactly("t2", "t3");
		assertThat(issues.records().get(0).requestCount24h()).isEqualTo(10);
		assertThat(issues.records().get(0).allowCount24h()).isEqualTo(4);

		var ready = service.overview(Map.of("status", "READY"));
		assertThat(ready.records()).extracting(RtaReadinessService.Route::monitorTaskId).containsExactly("t1");
	}

	@Test
	void overviewHonorsAdvertiserScopeAndExplicitTasks() {
		when(scope.constrain("other")).thenReturn("own");
		when(mapper.selectReadinessRoutes(eq("own"), isNull(), isNull(), eq(List.of("t1", "t2"))))
			.thenReturn(List.of());
		assertThat(service.overview(Map.of("advertiserId", "other", "monitorTaskIds", "t1, t2,t1")).total()).isZero();
		verify(mapper).selectReadinessRoutes(eq("own"), isNull(), isNull(), eq(List.of("t1", "t2")));
		verify(mapper, never()).selectRecentTaskStats(any(), anyList());
	}

	@Test
	void readinessRequiresVisibleTask() {
		when(scope.constrain(null)).thenReturn("own");
		when(mapper.selectReadinessRoutes("own", null, null, List.of("t9"))).thenReturn(List.of());
		assertThatThrownBy(() -> service.readiness("t9")).isInstanceOf(BootBusinessException.class).hasMessage("任务不可用");
		assertThatThrownBy(() -> service.readiness(" ")).hasMessage("请指定监测任务");
	}

	@Test
	void readinessReturnsRouteWithNotices() {
		when(scope.constrain(null)).thenReturn(null);
		when(mapper.selectReadinessRoutes(null, null, null, List.of("t1"))).thenReturn(List.of(genericRoute("t1")));
		var result = service.readiness("t1");
		assertThat(result.route().status()).isEqualTo(Status.READY);
		assertThat(result.notices()).isNotEmpty();
	}

}
