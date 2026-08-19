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
			.hasMessageContaining("尚未落库或已过期");
		verify(advertiserScope).requireSystemAdministrator();
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("flow-1"));
		assertThat(sql.getValue()).contains("d.req_id=CASE WHEN COALESCE(f.source_event_id,'')<>''")
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
	}

}
