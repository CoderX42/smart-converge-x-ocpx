package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxFlowLogServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void advertiserScopeOverridesForgedFlowLogFilter() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxFlowLogService(jdbcTemplate, advertiserScope).query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
				null, null, "advertiser-other", null, null, null, null, 1L, 20L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), args.capture());
		assertThat(sql.getValue()).contains("f.advertiser_id=?");
		assertThat(args.getValue()).contains("advertiser-own").doesNotContain("advertiser-other");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void requestDetailRequiresAdministratorAndUsesExactRouteRelationship() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("flow-1"))).thenReturn(List.of());

		OcpxFlowLogService service = new OcpxFlowLogService(jdbcTemplate, advertiserScope);

		assertThatThrownBy(() -> service.requestDetail("flow-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("流程日志不存在");
		verify(advertiserScope).requireSystemAdministrator();
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("flow-1"));
		assertThat(sql.getValue()).contains("LEFT JOIN scx_ocpx_log_feedback")
			.contains("d.req_id=CASE WHEN COALESCE(f.source_event_id,'')<>''")
			.contains("d.m_id=f.media_id")
			.contains("d.a_id=f.advertiser_id")
			.contains("d.t_id=f.monitor_task_id")
			.contains("d.l_id=f.promotion_link_id")
			.contains("d.model", "d.osv", "d.ipv6", "d.device_caid2");
	}

	@Test
	@SuppressWarnings("unchecked")
	void requestDetailRedactsSensitiveCallbackQueryValues() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("flow_log_id")).thenReturn("flow-1");
		when(resultSet.getString("flow_type")).thenReturn("FEEDBACK");
		when(resultSet.getString("event_code")).thenReturn("CLICK");
		when(resultSet.getString("result_status")).thenReturn("FAILED");
		when(resultSet.getString("message")).thenReturn("AERIM_DAILY_LIMIT");
		when(resultSet.getString("response_preview")).thenReturn("{\"code\":402,\"message\":\"limit\"}");
		when(resultSet.getObject("partner_http_status")).thenReturn(200);
		when(resultSet.getLong("duration_ms")).thenReturn(9L);
		when(resultSet.getString("req_id")).thenReturn("request-1");
		when(resultSet.getString("callback_url"))
			.thenReturn("https%3A%2F%2Fmedia.example%2Fcallback%3Frequest_id%3Drequest-1%26token%3Dprivate");
		when(resultSet.getString("m_id")).thenReturn("media-1");
		when(resultSet.getString("a_id")).thenReturn("advertiser-1");
		when(resultSet.getString("t_id")).thenReturn("task-1");
		when(resultSet.getString("l_id")).thenReturn("link-1");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("flow-1"))).thenAnswer(invocation -> {
			RowMapper<Object> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(resultSet, 0));
		});

		FlowLogViews.RequestDetail detail = new OcpxFlowLogService(jdbcTemplate, advertiserScope)
			.requestDetail("flow-1");

		assertThat(detail.getCallbackUrl()).isEqualTo("https://media.example/callback?request_id=request-1&token=***");
		assertThat(detail.getFlowLogId()).isEqualTo("flow-1");
		assertThat(detail.getResultStatus()).isEqualTo("FAILED");
		assertThat(detail.getPartnerHttpStatus()).isEqualTo(200);
		assertThat(detail.getMessage()).isEqualTo("AERIM_DAILY_LIMIT");
		assertThat(detail.getResponsePreview()).contains("\"code\":402");
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, times(2)).query(sql.capture(), any(RowMapper.class), eq("flow-1"));
		assertThat(sql.getAllValues().get(1)).contains("WHERE f.id=?").doesNotContain("WHEREf.id");
	}

	@Test
	@SuppressWarnings("unchecked")
	void requestDetailUsesTraceIdWithValidStageSql() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("flow_log_id")).thenReturn("flow-1");
		when(resultSet.getString("trace_id")).thenReturn("trace-1");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString())).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (!sql.contains("AS flow_log_id")) {
				return List.of();
			}
			RowMapper<Object> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(resultSet, 0));
		});

		FlowLogViews.RequestDetail detail = new OcpxFlowLogService(jdbcTemplate, advertiserScope)
			.requestDetail("flow-1");

		assertThat(detail.getTraceId()).isEqualTo("trace-1");
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("trace-1"));
		assertThat(sql.getValue()).contains("WHERE f.trace_id=?").doesNotContain("WHEREf.trace_id");
	}

}
