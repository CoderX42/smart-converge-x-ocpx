package com.yzytech.scx.ocpx.admin.mapper;

import com.yzytech.scx.ocpx.admin.dto.*;
import com.yzytech.scx.ocpx.admin.service.*;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs only against an explicitly selected loopback test schema; fixtures roll back. */
@EnabledIfSystemProperty(named = "ocpx.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/scx_.*test.*")
class XmlQueryMysqlIntegrationTest {

	private static final String ID = "xml-query-fixture";

	private static final LocalDate DAY = LocalDate.of(2026, 9, 8);

	@Test
	void dynamicQueriesPreserveProjectionNullsPaginationAndAllReportDimensions() {
		var ds = new DriverManagerDataSource(System.getProperty("ocpx.mysql.url"),
				System.getProperty("ocpx.mysql.user", "root"), System.getProperty("ocpx.mysql.password", ""));
		var jdbc = new JdbcTemplate(ds);
		new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(transaction -> {
			transaction.setRollbackOnly();
			seed(jdbc);
			AdvertiserScope scope = mock(AdvertiserScope.class);
			when(scope.constrain(any())).thenReturn(ID);
			when(scope.accountAdvertiserId(any())).thenReturn(ID);
			var reportMapper = XmlMapperTestSupport.mapper(OcpxUnifiedReportSqlMapper.class, jdbc);
			var reports = new OcpxUnifiedReportService(reportMapper, scope, mock(OcpxAdvertiserAccountService.class));
			for (String group : List.of("TIME", "ADVERTISER", "PROMOTION_LINK", "MEDIA", "MONITOR_TASK")) {
				var page = reports.query(new UnifiedReportQuery(DAY, DAY, group, ID, ID, ID, ID, 1, 30));
				assertThat(page.getRecords()).hasSize(1);
				assertThat(page.getSummary().get("UNIQUE_CLICK").getCount()).isEqualTo(5);
				assertThat(page.getSummary().get("BASE_PRICE").getAmount()).isEqualByComparingTo("12.123456");
			}
			for (int grain = 0; grain < 4; grain++) {
				var query = new SummaryReportQuery();
				query.setStartTime("20260908");
				query.setEndTime("20260908");
				query.setDateType(grain);
				query.setAdvertiserId(ID);
				query.setMediaId(ID);
				query.setPromotionLinkId(ID);
				query.setMonitorTaskId(ID);
				query.setCurrent(1L);
				query.setSize(10L);
				query.setAsc("uni_clk_smt");
				var page = reports.summary(query);
				assertThat(page.getRecords()).singleElement().satisfies(row -> {
					assertThat(row.getBasePrice()).isEqualByComparingTo("12.123456");
					assertThat(row.getLinkRadio()).isNull();
				});
			}
			var callback = new OcpxCallbackRecordService(
					XmlMapperTestSupport.mapper(OcpxCallbackRecordSqlMapper.class, jdbc), scope);
			var callbacks = callback.query(DAY, DAY, ID, ID, ID, ID, "ACTIVATE", true, "UNAVAILABLE", "xml-callback",
					1L, 10L);
			assertThat(callbacks.getRecords()).singleElement().satisfies(row -> {
				assertThat(row.isTargetConversion()).isTrue();
				assertThat(row.getDeductionDecision()).isNull();
				assertThat(row.getDeductionDecidedAt()).isNull();
				assertThat(row.getReceivedAt()).isEqualTo(DAY.atTime(10, 0));
			});
			var logs = new OcpxFlowLogService(XmlMapperTestSupport.mapper(OcpxFlowLogSqlMapper.class, jdbc), scope);
			var first = logs.query(DAY, DAY, null, null, ID, ID, ID, ID, null, 1L, 1L);
			assertThat(first.getRecords()).hasSize(1);
			assertThat(first.isHasNext()).isTrue();
			var second = logs.query(DAY, DAY, null, null, ID, ID, ID, ID, null, 2L, 1L);
			assertThat(second.getRecords()).hasSize(1);
			assertThat(second.isHasNext()).isFalse();
			var detail = logs.requestDetail(ID + "1");
			assertThat(detail.getFeedbackLogId()).isNull();
			assertThat(detail.getPartnerHttpStatus()).isNull();
			assertThat(detail.getStages()).hasSize(1);
			var billing = new OcpxBillingEntryService(
					XmlMapperTestSupport.mapper(OcpxBillingEntrySqlMapper.class, jdbc), scope);
			assertThat(billing
				.list(1, 10, ID, null, "RECHARGE", "POSTED", null, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())
				.getRecords()).singleElement()
				.satisfies(row -> assertThat(row.getAmount()).isEqualByComparingTo("20.000000"));
			assertThat(XmlMapperTestSupport.mapper(OcpxAdvertiserAccountSqlMapper.class, jdbc)
				.selectBalancesByIds(List.of())).isEmpty();
		});
	}

	private void seed(JdbcTemplate jdbc) {
		jdbc.update("INSERT INTO scx_ocpx_advertiser(id,advertiser_name) VALUES (?,?)", ID, ID);
		jdbc.update(
				"INSERT INTO scx_ocpx_promotion_link(id,advertiser_id,link_name,platform,link_url) VALUES (?,?,?,1,'https://example.invalid')",
				ID, ID, ID);
		jdbc.update("INSERT INTO scx_ocpx_media(id,media_name,uri) VALUES (?,?,'/feedback')", ID, ID);
		jdbc.update(
				"INSERT INTO scx_ocpx_monitor_task(id,monitor_name,advertiser_id,promotion_link_id,media_id) VALUES (?,?,?,?,?)",
				ID, ID, ID, ID, ID);
		jdbc.update(
				"INSERT INTO scx_ocpx_hourly_metric(time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,impression_count,click_count,spend_amount) VALUES (2026090810,?,?,?,?,100,5,12.123456)",
				ID, ID, ID, ID);
		jdbc.update(
				"INSERT INTO scx_ocpx_event_fact(id,fact_key,flow_type,event_code,request_id,advertiser_id,promotion_link_id,media_id,monitor_task_id,target_conversion,received_at) VALUES (?,?,'CALLBACK','ACTIVATE','xml-callback',?,?,?,?,1,?)",
				ID, "e".repeat(64), ID, ID, ID, ID, DAY.atTime(10, 0));
		for (int i = 1; i <= 2; i++)
			jdbc.update(
					"INSERT INTO scx_ocpx_flow_log(id,flow_type,event_id,event_code,payload_hash,advertiser_id,promotion_link_id,media_id,monitor_task_id,result_status,event_time) VALUES (?,'CALLBACK','xml-callback','ACTIVATE',?,?,?, ?,?,'SUCCESS',?)",
					ID + i, "f".repeat(64), ID, ID, ID, ID, DAY.atTime(10, i));
		jdbc.update(
				"INSERT INTO scx_ocpx_billing_entry(id,advertiser_id,entry_type,status,amount,balance_before,balance_after,business_key,occurred_at) VALUES (?,?,'RECHARGE','POSTED',20,0,20,?,?)",
				ID, ID, ID, DAY.atTime(10, 0));
	}

}
