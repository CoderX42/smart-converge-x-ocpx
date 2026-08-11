package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.config.OcpxReportingProperties;
import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class OcpxUnifiedReportServiceTest {

	private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxReportingProperties properties = new OcpxReportingProperties();

	private final OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate, accessScope, properties,
			new OcpxMetricDictionary());

	@Test
	void usesOneFrozenFactQueryAndReturnsNullForZeroDenominator() {
		when(accessScope.current()).thenReturn(allScope());
		when(jdbcTemplate.queryForObject(contains("grouped_count"), any(MapSqlParameterSource.class),
				org.mockito.ArgumentMatchers.eq(Long.class)))
			.thenReturn(1L);
		Map<String, Object> row = Map.ofEntries(Map.entry("dimensionKey", "2026-08-10 01:00:00|task-1"),
				Map.entry("impressions", 4), Map.entry("rawClicks", 2), Map.entry("validClicks", 0),
				Map.entry("duplicateClicks", 1), Map.entry("activations", 1), Map.entry("registrations", 0),
				Map.entry("payments", 0), Map.entry("orders", 0), Map.entry("costAmount", BigDecimal.ZERO),
				Map.entry("adjustmentCount", 0), Map.entry("deductionCount", 0), Map.entry("qualityStatus", "COMPLETE"),
				Map.entry("finalityStatus", "PROVISIONAL"), Map.entry("asOf", LocalDateTime.of(2026, 8, 10, 1, 0)));
		when(jdbcTemplate.queryForList(contains("GROUP BY CONCAT("), any(MapSqlParameterSource.class)))
			.thenReturn(List.of(row));
		when(jdbcTemplate.queryForMap(contains("FROM scx_ocpx_hourly_report h"), any(MapSqlParameterSource.class)))
			.thenReturn(row);
		try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
			permissions.when(() -> StpUtil.hasPermission("ocpx:report:metric:sensitive")).thenReturn(false);
			var result = service.query(new UnifiedReportQuery(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
					"HOUR", "TASK", null, null, null, null, "task-1", null, null,
					List.of("validClicks", "activationRate"), "dimension", "ASC", 1, 20));
			assertThat(result.total()).isEqualTo(1);
			assertThat(result.records().get(0).metrics().get("validClicks").value()).isEqualByComparingTo("0");
			assertThat(result.records().get(0).metrics().get("activationRate").value()).isNull();
			assertThat(result.records().get(0).metrics().get("activationRate").status()).isEqualTo("ZERO_DENOMINATOR");
			assertThat(result.records().get(0).dimensions().get("bucket")).isEqualTo("2026-08-10 01:00:00");
			assertThat(result.records().get(0).dimensions().get("objectId")).isEqualTo("task-1");
			assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
		}
	}

	@Test
	void rejectsUnknownMetricsAndExcessiveRangesBeforeSql() {
		try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
			permissions.when(() -> StpUtil.hasPermission("ocpx:report:metric:sensitive")).thenReturn(false);
			assertThatThrownBy(() -> service
				.query(new UnifiedReportQuery(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), "DAY", "TIME", null,
						null, null, null, null, null, null, List.of("inventedMetric"), null, null, 1, 20)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metrics");
			assertThatThrownBy(() -> service
				.query(new UnifiedReportQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 2), "DAY", "TIME", null,
						null, null, null, null, null, null, List.of("validClicks"), null, null, 1, 20)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("跨度");
		}
	}

	@Test
	void dashboardSessionRejectsExplicitOutOfScopeIdsBeforeWatermarkOrCache() {
		when(accessScope.current()).thenReturn(new OcpxAccessScope.Snapshot(9, null, Map
			.of(OcpxAccessScope.ScopeType.ADVERTISER, Map.of("advertiser-owned", OcpxAccessScope.PermissionLevel.VIEW))));
		try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
			permissions.when(() -> StpUtil.hasPermission("ocpx:report:metric:sensitive")).thenReturn(false);
			assertThatThrownBy(() -> service.openDashboardSession(new UnifiedReportQuery(LocalDate.of(2026, 8, 1),
					LocalDate.of(2026, 8, 10), "DAY", "TIME", "ACCOUNT", "advertiser-guessed", null, null, null, null,
					null, List.of("validClicks"), "dimension", "ASC", 1, 20)))
				.isInstanceOf(com.yzytech.scx.common.security.handler.BootBusinessException.class);
			verifyNoInteractions(jdbcTemplate);
		}
	}

	private OcpxAccessScope.Snapshot allScope() {
		return new OcpxAccessScope.Snapshot(1, OcpxAccessScope.PermissionLevel.MANAGE, Map.of());
	}

}
