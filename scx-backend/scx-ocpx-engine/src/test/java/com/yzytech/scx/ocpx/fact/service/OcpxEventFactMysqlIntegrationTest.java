package com.yzytech.scx.ocpx.fact.service;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "ocpx.mysql.url", matches = "jdbc:mysql:.+")
class OcpxEventFactMysqlIntegrationTest {

	private static final String ADVERTISER_ID = "itv108advertiser";

	private static final String LINK_ID = "itv108link";

	private static final String MEDIA_ID = "itv108media";

	private static final String TASK_ID = "itv108task";

	private JdbcTemplate jdbcTemplate;

	private TransactionTemplate transaction;

	private OcpxEventFactService service;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getProperty("ocpx.mysql.url"),
				System.getProperty("ocpx.mysql.user", "root"), System.getProperty("ocpx.mysql.password", ""));
		jdbcTemplate = new JdbcTemplate(dataSource);
		transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		service = new OcpxEventFactService(jdbcTemplate);
		cleanup();
		jdbcTemplate.update(
				"INSERT INTO scx_ocpx_advertiser (id,advertiser_name,bid_url) VALUES (?,?,?)", ADVERTISER_ID,
				"V108 integration advertiser", "https://example.invalid/callback");
		jdbcTemplate.update(
				"INSERT INTO scx_ocpx_promotion_link (id,advertiser_id,link_name,platform,link_url) VALUES (?,?,?,?,?)",
				LINK_ID, ADVERTISER_ID, "V108 integration link", 1, "https://example.invalid/link");
		jdbcTemplate.update("INSERT INTO scx_ocpx_media (id,media_name,uri) VALUES (?,?,?)", MEDIA_ID,
				"V108 integration media", "/feedback");
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_monitor_task (
				 id,monitor_name,advertiser_id,promotion_link_id,media_id,unit_price,conversion_event
				) VALUES (?,?,?,?,?,?,?)
				""", TASK_ID, "V108 integration task", ADVERTISER_ID, LINK_ID, MEDIA_ID,
				new BigDecimal("2.500000"), "ACTIVATE");
		jdbcTemplate.update(
				"INSERT INTO scx_ocpx_advertiser_account (advertiser_id,currency,available_balance) VALUES (?,'CNY',?)",
				ADVERTISER_ID, new BigDecimal("100.000000"));
	}

	@AfterEach
	void tearDown() {
		if (jdbcTemplate != null) {
			jdbcTemplate.execute("DROP TRIGGER IF EXISTS it_v108_fail_hourly");
			cleanup();
		}
	}

	@Test
	void closesTheExactHundredImpressionTenClickTwoConversionExampleAndDeduplicatesRetries() {
		for (int index = 0; index < 100; index++) {
			recordFeedback("request-" + index, EventTypeEnum.IMP);
		}
		for (int index = 0; index < 10; index++) {
			assertThat(recordFeedback("request-" + index, EventTypeEnum.CLICK).forwardAllowed()).isTrue();
		}
		for (int index = 0; index < 2; index++) {
			recordCallback("request-" + index);
		}
		assertAcceptanceTotals();

		for (int index = 0; index < 100; index++) {
			assertThat(recordFeedback("request-" + index, EventTypeEnum.IMP).firstEvent()).isFalse();
		}
		for (int index = 0; index < 10; index++) {
			assertThat(recordFeedback("request-" + index, EventTypeEnum.CLICK).forwardAllowed()).isFalse();
		}
		for (int index = 0; index < 2; index++) {
			assertThat(recordCallback("request-" + index).firstEvent()).isFalse();
		}
		assertAcceptanceTotals();
	}

	@Test
	void serializesConcurrentClicksAndRollsBackEveryFinancialProjectionOnFailure() throws Exception {
		jdbcTemplate.update("UPDATE scx_ocpx_advertiser_account SET available_balance=2.500000 WHERE advertiser_id=?",
				ADVERTISER_ID);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<java.util.concurrent.Future<OcpxEventFactService.FeedbackFactResult>> futures = new ArrayList<>();
			for (int index = 0; index < 2; index++) {
				String requestId = "concurrent-" + index;
				futures.add(executor.submit(() -> {
					start.await();
					return recordFeedback(requestId, EventTypeEnum.CLICK);
				}));
			}
			start.countDown();
			List<OcpxEventFactService.FeedbackFactResult> results = new ArrayList<>();
			for (var future : futures) {
				results.add(future.get());
			}
			assertThat(results).extracting(OcpxEventFactService.FeedbackFactResult::billingStatus)
					.containsExactlyInAnyOrder("POSTED", "REJECTED_INSUFFICIENT_BALANCE");
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(balance()).isEqualByComparingTo("0.000000");
		assertThat(scalar("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=?"))
			.isEqualTo(2L);
		assertThat(scalar("SELECT SUM(click_count) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualTo(2L);
		assertThat(amount("SELECT SUM(spend_amount) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualByComparingTo("2.500000");
		assertThat(scalar("SELECT SUM(rejected_click_count) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualTo(1L);

		clearFactsAndAccounting();
		jdbcTemplate.update("UPDATE scx_ocpx_advertiser_account SET available_balance=100.000000 WHERE advertiser_id=?",
				ADVERTISER_ID);
		jdbcTemplate.execute("""
				CREATE TRIGGER it_v108_fail_hourly BEFORE INSERT ON scx_ocpx_hourly_metric
				FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced hourly failure'
				""");
		assertThatThrownBy(() -> recordFeedback("rollback-click", EventTypeEnum.CLICK))
			.isInstanceOf(RuntimeException.class);
		jdbcTemplate.execute("DROP TRIGGER it_v108_fail_hourly");
		assertThat(balance()).isEqualByComparingTo("100.000000");
		assertThat(scalar("SELECT COUNT(*) FROM scx_ocpx_event_fact WHERE advertiser_id=?")).isZero();
		assertThat(scalar("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=?")).isZero();
	}

	private void assertAcceptanceTotals() {
		assertThat(balance()).isEqualByComparingTo("75.000000");
		assertThat(scalar("SELECT COUNT(*) FROM scx_ocpx_event_fact WHERE advertiser_id=?")).isEqualTo(112L);
		assertThat(scalar("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=? AND status='POSTED'"))
			.isEqualTo(10L);
		assertThat(scalar("SELECT SUM(impression_count) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualTo(100L);
		assertThat(scalar("SELECT SUM(click_count) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualTo(10L);
		assertThat(scalar("SELECT SUM(target_conversion_count) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualTo(2L);
		assertThat(amount("SELECT SUM(spend_amount) FROM scx_ocpx_hourly_metric WHERE advertiser_id=?"))
			.isEqualByComparingTo("25.000000");
	}

	private OcpxEventFactService.FeedbackFactResult recordFeedback(String requestId, EventTypeEnum event) {
		return transaction.execute(status -> service.recordFeedback(feedback(requestId, event)));
	}

	private OcpxEventFactService.CallbackFactResult recordCallback(String requestId) {
		return transaction.execute(status -> service.recordCallback(CallbackReq.builder()
			.feedback(feedback(requestId, EventTypeEnum.CLICK))
			.actionType(ActionTypeEnum.ACTIVATE)
			.build()));
	}

	private FeedbackReq feedback(String requestId, EventTypeEnum event) {
		return FeedbackReq.builder()
			.reqId(requestId)
			.aId(ADVERTISER_ID)
			.lId(LINK_ID)
			.mId(MEDIA_ID)
			.tId(TASK_ID)
			.event(event)
			.build();
	}

	private BigDecimal balance() {
		return jdbcTemplate.queryForObject(
				"SELECT available_balance FROM scx_ocpx_advertiser_account WHERE advertiser_id=?", BigDecimal.class,
				ADVERTISER_ID);
	}

	private Long scalar(String sql) {
		return jdbcTemplate.queryForObject(sql, Long.class, ADVERTISER_ID);
	}

	private BigDecimal amount(String sql) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, ADVERTISER_ID);
	}

	private void clearFactsAndAccounting() {
		jdbcTemplate.update("DELETE FROM scx_ocpx_billing_entry WHERE advertiser_id=?", ADVERTISER_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_event_fact WHERE advertiser_id=?", ADVERTISER_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_hourly_metric WHERE advertiser_id=?", ADVERTISER_ID);
	}

	private void cleanup() {
		jdbcTemplate.execute("DROP TRIGGER IF EXISTS it_v108_fail_hourly");
		clearFactsAndAccounting();
		jdbcTemplate.update("DELETE FROM scx_ocpx_monitor_task WHERE id=?", TASK_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_advertiser_account WHERE advertiser_id=?", ADVERTISER_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_promotion_link WHERE id=?", LINK_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_media WHERE id=?", MEDIA_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_advertiser WHERE id=?", ADVERTISER_ID);
	}

}
