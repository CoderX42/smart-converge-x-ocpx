package com.yzytech.scx.ocpx.engine;

import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.billing.JdbcBillingPort;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in MySQL 8 acceptance for the strict quota and CPA transaction boundary.
 *
 * <p>
 * The normal test suite skips this test. Run it only against an isolated database with
 * {@code -Dscx.test.mysql.url=jdbc:mysql://127.0.0.1:3306/smart_converge_x} and matching credentials.
 * </p>
 */
class OcpxMysqlConcurrencyAcceptanceTest {

	@Test
	@Timeout(60)
	void strictlyAcceptsTenOfFiftyUniqueCallbacksAndBalancesEveryLedgerEntry() throws Exception {
		String url = System.getProperty("scx.test.mysql.url");
		Assumptions.assumeTrue(url != null && !url.isBlank(), "isolated MySQL URL is not configured");
		String username = System.getProperty("scx.test.mysql.username", "root");
		String password = System.getProperty("scx.test.mysql.password", "");

		DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		JdbcBillingPort billing = new JdbcBillingPort(jdbc);
		OcpxPersistenceRepository repository = new OcpxPersistenceRepository(jdbc, null, null, null, billing);

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String advertiserId = "it_adv_" + suffix;
		String monitorId = "it_mon_" + suffix;
		String eventPrefix = "it_evt_" + suffix + "_";
		BigDecimal initialBalance = new BigDecimal("100.000000");
		BigDecimal assessmentPrice = new BigDecimal("0.500000");

		jdbc.update("""
				INSERT INTO scx_adx_advertiser
				(id,advertiser_name,adapter_code,bid_url,account,password_hash,balance,status,config_version,deleted)
				VALUES (?,?, 'COCO','https://example.com/callback',?,?,?,'ACTIVE',1,0)
				""", advertiserId, "IT advertiser " + suffix, "it_account_" + suffix, "not-a-real-password",
				initialBalance);

		RouteEventSnapshot eventMapping = new RouteEventSnapshot("it_mapping_" + suffix, "ACTIVATE", "激活",
				"ACTIVATE", "激活", "ACTIVATE", "激活", assessmentPrice, 10);
		RouteSnapshot route = new RouteSnapshot(monitorId, "it_biz_" + suffix, 1, "it_channel_" + suffix, "IT",
				"STANDARD", "HMAC", 1, "not-a-real-secret", advertiserId, "it_app_" + suffix, "COCO",
				"not-a-real-secret", "https://example.com/upstream", "", "https://example.com/downstream",
				BigDecimal.ZERO, BigDecimal.ONE, "OFF", "OFF", 3600, List.of(eventMapping), 1);
		CallbackContext context = new CallbackContext("it_ref_" + suffix, "it_feedback_" + suffix, route, null,
				null);

		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		try {
			for (int index = 0; index < 50; index++) {
				String eventId = eventPrefix + index;
				futures.add(pool.submit(() -> {
					start.await();
					return transactions.execute(status -> repository
						.persistCallback(callback(eventId, context, assessmentPrice), context, true, "IT_ACCEPT")
						.status());
				}));
			}
			start.countDown();

			List<String> statuses = new ArrayList<>();
			for (Future<String> future : futures) {
				statuses.add(future.get(45, TimeUnit.SECONDS));
			}

			assertThat(statuses).filteredOn("ACCEPTED"::equals).hasSize(10);
			assertThat(statuses).filteredOn("LIMIT_EXCEEDED"::equals).hasSize(40);
			assertThat(count(jdbc, "SELECT COUNT(*) FROM scx_ocpx_event WHERE event_id LIKE ?", eventPrefix + "%"))
				.isEqualTo(10);
			assertThat(count(jdbc,
					"SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='CALLBACK_DOWNSTREAM' AND event_id LIKE ?",
					eventPrefix + "%")).isEqualTo(10);
			assertThat(count(jdbc,
					"SELECT queued_count FROM scx_ocpx_event_daily_quota WHERE monitor_id=? AND upstream_event_code='ACTIVATE'",
					monitorId)).isEqualTo(10);
			assertThat(count(jdbc,
					"SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE mode='CPA' AND status='POSTED' AND charge_event_id LIKE ?",
					eventPrefix + "%")).isEqualTo(10);
			assertThat(count(jdbc,
					"SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE charge_event_id LIKE ? AND channel_id=? AND app_link_id=? AND monitor_id=? AND source_event_code='ACTIVATE' AND conversion_event_code='ACTIVATE' AND media_event_code='ACTIVATE'",
					eventPrefix + "%", route.channelId(), route.appLinkId(), monitorId)).isEqualTo(10);
			BigDecimal posted = jdbc.queryForObject(
					"SELECT COALESCE(SUM(amount),0) FROM scx_ocpx_billing_entry WHERE mode='CPA' AND status='POSTED' AND charge_event_id LIKE ?",
					BigDecimal.class, eventPrefix + "%");
			BigDecimal balance = jdbc.queryForObject("SELECT balance FROM scx_adx_advertiser WHERE id=?",
					BigDecimal.class, advertiserId);
			assertThat(posted).isEqualByComparingTo("5.000000");
			assertThat(initialBalance.subtract(balance)).isEqualByComparingTo(posted);
		}
		finally {
			pool.shutdownNow();
			jdbc.update("DELETE FROM scx_ocpx_delivery_job WHERE event_id LIKE ?", eventPrefix + "%");
			jdbc.update("DELETE FROM scx_ocpx_deduction_decision WHERE callback_event_id LIKE ?", eventPrefix + "%");
			jdbc.update("DELETE FROM scx_ocpx_event WHERE event_id LIKE ?", eventPrefix + "%");
			jdbc.update("DELETE FROM scx_ocpx_billing_entry WHERE charge_event_id LIKE ?", eventPrefix + "%");
			jdbc.update("DELETE FROM scx_adx_hourly_report WHERE monitor_id=?", monitorId);
			jdbc.update("DELETE FROM scx_ocpx_event_daily_quota WHERE monitor_id=?", monitorId);
			jdbc.update("DELETE FROM scx_adx_advertiser WHERE id=?", advertiserId);
		}
	}

	private NormalizedCallbackEvent callback(String eventId, CallbackContext context, BigDecimal assessmentPrice) {
		return new NormalizedCallbackEvent(eventId, context.feedbackEventId(), context.callbackRef(),
				OcpxEventType.ACTIVATE, OffsetDateTime.now(), null, null, "ACTIVATE", "ACTIVATE", "ACTIVATE", "ACTIVATE",
				assessmentPrice, 10);
	}

	private long count(JdbcTemplate jdbc, String sql, Object... arguments) {
		Long value = jdbc.queryForObject(sql, Long.class, arguments);
		return value == null ? 0 : value;
	}

}
