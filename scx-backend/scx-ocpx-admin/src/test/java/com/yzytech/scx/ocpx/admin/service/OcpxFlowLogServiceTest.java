package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxFlowLogSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxFlowLogServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void detectsTheNextPageWithoutCountingAndRejectsPagesBeyondTheSearchWindow() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenReturn(Collections.nCopies(31, mock(FlowLogViews.Item.class)));
		OcpxFlowLogService service = new OcpxFlowLogService(
				XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope);

		FlowLogViews.Page page = service.query(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 25), null, null, null,
				null, null, null, null, 1L, 30L);

		assertThat(page.getRecords()).hasSize(30);
		assertThat(page.getTotal()).isEqualTo(31L);
		assertThat(page.isHasNext()).isTrue();
		service.query(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 25), null, null, null, null, null, null, null,
				334L, 30L);
		ArgumentCaptor<Object[]> pageArgs = ArgumentCaptor.forClass(Object[].class);
		ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, times(2)).query(pageSql.capture(), any(RowMapper.class), pageArgs.capture());
		assertThat(pageSql.getAllValues())
			.allSatisfy(sql -> assertThat(sql).contains("FORCE INDEX (idx_ocpx_flow_log_page)"));
		assertThat(pageArgs.getAllValues().get(0)).endsWith(31L, 0L);
		assertThat(pageArgs.getAllValues().get(1)).endsWith(11L, 9_990L);
		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
		assertThatThrownBy(() -> service.query(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 25), null, null, null,
				null, null, null, null, 335L, 30L))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("翻页超过 10000 条");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void optionsUseFlowLogScopeAndParentDimensions() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.currentAdvertiserId()).thenReturn("advertiser-own");
		when(advertiserScope.constrain("advertiser-forged")).thenReturn("advertiser-own");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxFlowLogService(XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope)
			.options("advertiser-forged", "media-1");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, times(4)).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("scx_ocpx_advertiser WHERE id=?"))
			.anyMatch(value -> value.contains("scx_ocpx_promotion_link") && value.contains("advertiser_id=?"))
			.anyMatch(value -> value.contains("scx_ocpx_media") && value.contains("t.advertiser_id=?"))
			.anyMatch(value -> value.contains("scx_ocpx_monitor_task") && value.contains("media_id=?"));
		assertThat(args.getAllValues()).allSatisfy(values -> assertThat(values).doesNotContain("advertiser-forged"));
		assertThat(args.getAllValues()).anySatisfy(values -> assertThat(values).contains("advertiser-own", "media-1"));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void queryAppliesAllFourBusinessDimensionFilters() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-1")).thenReturn("advertiser-1");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxFlowLogService(XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope)
			.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, "advertiser-1", "link-1", "media-1",
					"task-1", null, 1L, 20L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue())
			.contains("f.advertiser_id=?", "f.promotion_link_id=?", "f.media_id=?", "f.monitor_task_id=?",
					"ORDER BY f.event_time DESC,f.id DESC LIMIT ? OFFSET ?")
			.doesNotContain("ORDER BY f.create_time", "FORCE INDEX");
		assertThat(args.getValue()).contains("advertiser-1", "link-1", "media-1", "task-1").endsWith(21L, 0L);
		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void advertiserScopeOverridesForgedFlowLogFilter() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		new OcpxFlowLogService(XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope)
			.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, "advertiser-other", null, null, null,
					null, 1L, 20L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("f.advertiser_id=?");
		assertThat(args.getValue()).contains("advertiser-own").doesNotContain("advertiser-other");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void requestDetailRequiresAdministratorAndUsesExactRouteRelationship() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AdvertiserScope advertiserScope = mock(AdvertiserScope.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("flow-1"))).thenReturn(List.of());

		OcpxFlowLogService service = new OcpxFlowLogService(
				XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope);

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

		FlowLogViews.RequestDetail detail = new OcpxFlowLogService(
				XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope)
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

		FlowLogViews.RequestDetail detail = new OcpxFlowLogService(
				XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbcTemplate), advertiserScope)
			.requestDetail("flow-1");

		assertThat(detail.getTraceId()).isEqualTo("trace-1");
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("trace-1"));
		assertThat(sql.getValue()).contains("WHERE f.trace_id=?").doesNotContain("WHEREf.trace_id");
	}

}
