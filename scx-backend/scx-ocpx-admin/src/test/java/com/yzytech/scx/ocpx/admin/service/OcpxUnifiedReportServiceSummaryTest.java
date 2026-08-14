package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportViews;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxUnifiedReportServiceSummaryTest {

	private JdbcTemplate jdbcTemplate;

	private OcpxUnifiedReportService service;

	private AdvertiserScope advertiserScope;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		advertiserScope = mock(AdvertiserScope.class);
		when(advertiserScope.constrain(nullable(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service = new OcpxUnifiedReportService(jdbcTemplate, advertiserScope);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void truncatesTimeHourByDateType() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		for (int dateType = 0; dateType <= 3; dateType++) {
			service.summary(query(dateType, null, null));
		}

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, times(4)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		List<String> selects = sql.getAllValues();
		assertThat(selects.get(0)).contains("r.time_hour AS reportTime")
			.contains("GROUP BY r.m_id,r.a_id,r.t_id,r.l_id,r.time_hour");
		assertThat(selects.get(1)).contains("LEFT(r.time_hour,8) AS reportTime")
			.contains("GROUP BY r.m_id,r.a_id,r.t_id,r.l_id,LEFT(r.time_hour,8)");
		assertThat(selects.get(2)).contains("LEFT(r.time_hour,6) AS reportTime")
			.contains("GROUP BY r.m_id,r.a_id,r.t_id,r.l_id,LEFT(r.time_hour,6)");
		assertThat(selects.get(3)).contains("LEFT(r.time_hour,4) AS reportTime")
			.contains("GROUP BY r.m_id,r.a_id,r.t_id,r.l_id,LEFT(r.time_hour,4)");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void mapsNamesRatioAndComputedRates() throws Exception {
		ResultSet rs = summaryRow(0.6d);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));

		SummaryReportViews.Page page = service.summary(query(1, null, null));

		assertThat(page.getTotal()).isEqualTo(1);
		SummaryReportViews.Row row = page.getRecords().get(0);
		assertThat(row.getReportTime()).isEqualTo("20260812");
		assertThat(row.getMediaId()).isEqualTo("media-1");
		assertThat(row.getMediaName()).isEqualTo("小米");
		assertThat(row.getMonitorTaskId()).isEqualTo("task-1");
		assertThat(row.getMonitorName()).isEqualTo("任务一");
		assertThat(row.getAdvertiserId()).isEqualTo("advertiser-1");
		assertThat(row.getAdvertiserName()).isEqualTo("KIMI");
		assertThat(row.getPromotionLinkId()).isEqualTo("link-1");
		assertThat(row.getPromotionLinkName()).isEqualTo("链接一");
		assertThat(row.getLinkRadio()).isEqualTo("0.6");
		assertThat(row.getUniShowSmt()).isEqualTo(1000);
		assertThat(row.getUniClkSmt()).isEqualTo(120);
		assertThat(row.getClickRate()).isEqualTo(12.0d);
		assertThat(row.getBasePrice()).isEqualByComparingTo(new BigDecimal("2.00"));
		assertThat(row.getActivate()).isEqualTo(11);
		assertThat(row.getFinishTheOrder()).isEqualTo(21);
		assertThat(row.getFirstActivation()).isEqualTo(22);
		assertThat(row.getFirstOrder()).isEqualTo(23);
		assertThat(row.getCurrencyAmount()).isEqualByComparingTo("123.45");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains(
				"LEFT JOIN scx_ocpx_deduction_rule dr ON dr.media_id=stat.m_id AND dr.promotion_link_id=stat.l_id AND dr.is_delete=0");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void leavesLinkRadioNullWhenNoActiveDeductionRule() throws Exception {
		ResultSet rs = summaryRow(null);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));

		SummaryReportViews.Page page = service.summary(query(1, null, null));

		assertThat(page.getRecords().get(0).getLinkRadio()).isNull();
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void appliesWhitelistedOrderingAndIgnoresIllegalColumns() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		service.summary(query(1, "uni_show_smt", null));
		service.summary(query(1, null, "click_rate"));
		service.summary(query(1, "report_time", "base_price"));
		service.summary(query(1, "uni_show_smt;DROP TABLE scx_ocpx_report_mat", null));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, times(4)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getAllValues().get(0)).contains("ORDER BY uni_show_smt ASC");
		assertThat(sql.getAllValues().get(1)).contains("ORDER BY clickRate DESC");
		assertThat(sql.getAllValues().get(2)).contains("ORDER BY reportTime ASC");
		assertThat(sql.getAllValues().get(3)).contains("ORDER BY reportTime DESC").doesNotContain("DROP");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void appliesFiltersAndPagination() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		service.summary(new SummaryReportQuery(1, "media-1", "task-1", "advertiser-1", "link-1", "20260801", "20260812",
				null, null, 2L, 10L));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("AND r.m_id=?")
			.contains("AND r.t_id=?")
			.contains("AND r.a_id=?")
			.contains("AND r.l_id=?")
			.contains("r.time_hour BETWEEN ? AND ?")
			.contains("LIMIT ? OFFSET ?");
		Object[] values = args.getValue();
		assertThat(values).startsWith(2026080100, 2026081223, "media-1", "task-1", "advertiser-1", "link-1");
		assertThat(values).endsWith(10, 10);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void advertiserScopeOverridesForgedReportFilter() {
		when(advertiserScope.constrain("advertiser-other")).thenReturn("advertiser-own");
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		service.summary(new SummaryReportQuery(1, null, null, "advertiser-other", null, "20260801", "20260812", null,
				null, 1L, 10L));

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
		assertThat(args.getValue()).contains("advertiser-own").doesNotContain("advertiser-other");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void rejectsInvalidDateTypeAndInvalidTimeRange() {
		assertThatThrownBy(() -> service.summary(query(4, null, null))).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("dateType");
		assertThatThrownBy(() -> service
			.summary(new SummaryReportQuery(1, null, null, null, null, null, "20260812", null, null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("报表时间范围无效");
		assertThatThrownBy(() -> service.summary(
				new SummaryReportQuery(1, null, null, null, null, "2026-08-01", "20260812", null, null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("报表时间范围无效");
		assertThatThrownBy(() -> service
			.summary(new SummaryReportQuery(1, null, null, null, null, "20260812", "20260801", null, null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("报表时间范围无效");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void exportsCsvWithChineseHeaderAndZeroForMissingRatio() throws Exception {
		ResultSet rs = summaryRow(null);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));

		String csv = service.summaryCsv(query(1, null, null));

		String[] lines = csv.split("\n");
		assertThat(lines).hasSize(2);
		assertThat(lines[0]).isEqualTo(
				"报告时间,媒体名称,监测名称,广告主名称,链接名称,链接扣量,去重展示数,去重点击数,点击率,支出,激活,唤起," + "登录,注册,付费,获客,次留,访问,认证,下单,完单,首活,首购,订单金额");
		assertThat(lines[1]).isEqualTo(
				"20260812,小米,任务一,KIMI,链接一,0,1000,120,12.0,2.00,11,12,13,14,15,16,17,18," + "19,20,21,22,23,123.45");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void exportsEmptyCsvWhenNoRows() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		assertThat(service.summaryCsv(query(1, null, null))).isEmpty();
	}

	private SummaryReportQuery query(int dateType, String asc, String desc) {
		return new SummaryReportQuery(dateType, null, null, null, null, "20260801", "20260812", asc, desc, null, null);
	}

	private ResultSet summaryRow(Double ratio) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		lenient().when(rs.getString("reportTime")).thenReturn("20260812");
		lenient().when(rs.getString("m_id")).thenReturn("media-1");
		lenient().when(rs.getString("media_name")).thenReturn("小米");
		lenient().when(rs.getString("t_id")).thenReturn("task-1");
		lenient().when(rs.getString("monitor_name")).thenReturn("任务一");
		lenient().when(rs.getString("a_id")).thenReturn("advertiser-1");
		lenient().when(rs.getString("advertiser_name")).thenReturn("KIMI");
		lenient().when(rs.getString("l_id")).thenReturn("link-1");
		lenient().when(rs.getString("link_name")).thenReturn("链接一");
		lenient().when(rs.getObject("link_radio")).thenReturn(ratio);
		lenient().when(rs.getLong("uni_show_smt")).thenReturn(1000L);
		lenient().when(rs.getLong("uni_clk_smt")).thenReturn(120L);
		lenient().when(rs.getDouble("clickRate")).thenReturn(12.0d);
		lenient().when(rs.getBigDecimal("base_price")).thenReturn(new BigDecimal("2.00"));
		long action = 11;
		for (String column : List.of("activate", "evoke", "login", "register", "payment", "customer_acquisition",
				"next_stay", "visits", "authentication", "place_order", "finish_the_order", "first_activation",
				"first_order")) {
			lenient().when(rs.getLong(column)).thenReturn(action++);
		}
		lenient().when(rs.getBigDecimal("currency_amount")).thenReturn(new BigDecimal("123.45"));
		return rs;
	}

}
