package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.config.OcpxRuntimeCenterProperties;
import com.yzytech.scx.ocpx.admin.dto.RuntimeRequests;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.config.OcpxCryptoProperties;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class OcpxRuntimeServiceTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxRuntimeCenterProperties properties = new OcpxRuntimeCenterProperties();

	private final ProtocolCrypto crypto = crypto();

	private final OcpxRuntimeService service = new OcpxRuntimeService(jdbcTemplate, accessScope, crypto, properties);

	@BeforeEach
	void scope() {
		Map<OcpxAccessScope.ScopeType, Map<String, OcpxAccessScope.PermissionLevel>> grants = Map.of(
				OcpxAccessScope.ScopeType.ADVERTISER, Map.of("adv-1", OcpxAccessScope.PermissionLevel.MANAGE),
				OcpxAccessScope.ScopeType.CHANNEL, Map.of("channel-1", OcpxAccessScope.PermissionLevel.MANAGE),
				OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, Map.of("media-1", OcpxAccessScope.PermissionLevel.MANAGE));
		when(accessScope.current()).thenReturn(new OcpxAccessScope.Snapshot(9, null, grants));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void factsApplyThreeObjectScopeAndReturnExplicitFreshness() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		var page = service.facts(query(null, null, null));

		assertThat(page.records()).isEmpty();
		assertThat(page.freshnessStatus()).isEqualTo("FINAL");
		assertThat(page.traceId()).isNotBlank();
		org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
		assertThat(sql.getValue()).contains("f.advertiser_id IN (?)", "f.channel_id IN (?)",
				"f.media_account_id IN (?)", "f.received_time>=?", "f.received_time<?");
	}

	@Test
	void rejectsUnboundedOrArbitraryQueriesBeforeTouchingFacts() {
		RuntimeRequests.Query invalidSort = query(null, null, "DROP TABLE");
		assertThatThrownBy(() -> service.facts(invalidSort)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("sort");
		RuntimeRequests.Query tooWide = query(LocalDateTime.now().minusDays(9), LocalDateTime.now(), null);
		assertThatThrownBy(() -> service.facts(tooWide)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不超过");
		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void previewIsReadOnlyAndTamperedTokenCannotCreateACommand() throws Exception {
		stubFailureQueries(new AtomicBoolean(false));
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
				MockedStatic<StpUtil> safe = mockStatic(StpUtil.class)) {
			security.when(SecurityUtils::getUserId).thenReturn("user-1");
			var preview = service.replayPreview(new RuntimeRequests.ReplayPreview("failure-1", 1L));
			assertThat(preview.blockers()).isEmpty();
			assertThat(preview.mode()).isEqualTo("ORIGINAL_SNAPSHOT_ONLY");
			assertThat(preview.workerStatus()).isEqualTo("DISABLED");
			assertThat(preview.previewToken()).doesNotContain("failure-1");

			RuntimeRequests.ReplayConfirm tampered = new RuntimeRequests.ReplayConfirm("request_1234567890",
					"failure-1", 1L, preview.previewToken() + "x", "人工核验后补偿", "T-1", true);
			assertThatThrownBy(() -> service.replay(tampered)).isInstanceOf(BootBusinessException.class)
				.hasMessageContaining("预览");
			verify(jdbcTemplate, never()).update(
					org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_runtime_replay_request"),
					any(Object[].class));
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void confirmCreatesOnlyOneQueuedCommandAndReturnsTheIdempotentResult() throws Exception {
		AtomicBoolean inserted = new AtomicBoolean(false);
		stubFailureQueries(inserted);
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
			if (((String) invocation.getArgument(0)).contains("INSERT INTO scx_ocpx_runtime_replay_request")) {
				inserted.set(true);
			}
			return 1;
		});
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
				MockedStatic<StpUtil> safe = mockStatic(StpUtil.class)) {
			security.when(SecurityUtils::getUserId).thenReturn("user-1");
			var preview = service.replayPreview(new RuntimeRequests.ReplayPreview("failure-1", 1L));
			var request = new RuntimeRequests.ReplayConfirm("request_1234567890", "failure-1", 1L,
					preview.previewToken(), "人工核验后补偿", "T-1", true);

			var first = service.replay(request);
			var second = service.replay(request);

			assertThat(first.status()).isEqualTo("QUEUED");
			assertThat(second.commandId()).isEqualTo(first.commandId());
			assertThat(first.workerStatus()).isEqualTo("DISABLED");
			verify(jdbcTemplate).update(
					org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_runtime_replay_request"),
					any(Object[].class));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void stubFailureQueries(AtomicBoolean inserted) throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			RowMapper mapper = invocation.getArgument(1);
			if (sql.contains("FROM scx_ocpx_runtime_failure d WHERE d.id=?")) {
				return List.of(mapper.mapRow(failureResultSet(), 0));
			}
			if (sql.contains("FROM scx_ocpx_runtime_replay_request r") && sql.contains("r.request_id=?")
					&& inserted.get()) {
				return List.of(mapper.mapRow(replayResultSet(), 0));
			}
			return List.of();
		});
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("FROM scx_ocpx_route_version"),
				eq(Long.class), any(Object[].class)))
			.thenReturn(1L);
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("scx_ocpx_route_runtime_state"),
				eq(Long.class), any(Object[].class)))
			.thenReturn(0L);
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("source_failure_id=?"), eq(Long.class),
				any(Object[].class)))
			.thenReturn(0L);
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
	}

	private ResultSet failureResultSet() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString(1)).thenReturn("failure-1");
		when(rs.getLong(2)).thenReturn(1L);
		when(rs.getString(3)).thenReturn("RETRYABLE");
		when(rs.getString(4)).thenReturn("TEMPORARY_TIMEOUT");
		when(rs.getBoolean(5)).thenReturn(true);
		when(rs.getString(6)).thenReturn("tenant-1");
		when(rs.getString(7)).thenReturn("adv-1");
		when(rs.getString(8)).thenReturn("product-1");
		when(rs.getString(9)).thenReturn("link-1");
		when(rs.getString(10)).thenReturn("task-1");
		when(rs.getString(11)).thenReturn("channel-1");
		when(rs.getString(12)).thenReturn("media-1");
		when(rs.getString(13)).thenReturn("operator-1");
		when(rs.getString(14)).thenReturn("route-1");
		when(rs.getString(15)).thenReturn("version-1");
		when(rs.getLong(16)).thenReturn(3L);
		when(rs.getObject(17)).thenReturn(2);
		when(rs.getString(18)).thenReturn("FROZEN");
		return rs;
	}

	private ResultSet replayResultSet() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString(1)).thenReturn("request_1234567890");
		when(rs.getString(2)).thenReturn("command-1");
		when(rs.getString(3)).thenReturn("failure-1");
		when(rs.getString(4)).thenReturn("task-1");
		when(rs.getString(5)).thenReturn("version-1");
		when(rs.getLong(6)).thenReturn(3L);
		when(rs.getString(7)).thenReturn("ORIGINAL_SNAPSHOT_ONLY");
		when(rs.getString(8)).thenReturn("QUEUED");
		when(rs.getInt(9)).thenReturn(0);
		when(rs.getInt(10)).thenReturn(5);
		when(rs.getTimestamp(13)).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.now()));
		when(rs.getTimestamp(15)).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.now()));
		return rs;
	}

	private RuntimeRequests.Query query(LocalDateTime start, LocalDateTime end, String sort) {
		return new RuntimeRequests.Query(1L, 20L, start, end, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, sort);
	}

	private ProtocolCrypto crypto() {
		OcpxCryptoProperties runtime = new OcpxCryptoProperties();
		runtime.setPayloadAesKey("unit-test-only-runtime-key");
		return new ProtocolCrypto(runtime);
	}

}
