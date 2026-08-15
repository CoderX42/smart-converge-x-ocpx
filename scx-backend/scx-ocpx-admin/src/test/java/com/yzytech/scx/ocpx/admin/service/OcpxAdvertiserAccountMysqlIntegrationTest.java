package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
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
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "ocpx.mysql.url", matches = "jdbc:mysql:.+")
class OcpxAdvertiserAccountMysqlIntegrationTest {

	private static final String ADVERTISER_ID = "itv108recharge";

	private JdbcTemplate jdbcTemplate;

	private TransactionTemplate transaction;

	private OcpxAdvertiserAccountService service;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getProperty("ocpx.mysql.url"),
				System.getProperty("ocpx.mysql.user", "root"), System.getProperty("ocpx.mysql.password", ""));
		jdbcTemplate = new JdbcTemplate(dataSource);
		transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		service = new OcpxAdvertiserAccountService(jdbcTemplate, mock(AdvertiserScope.class));
		cleanup();
		jdbcTemplate.update(
				"INSERT INTO scx_ocpx_advertiser (id,advertiser_name,bid_url) VALUES (?,?,?)", ADVERTISER_ID,
				"V108 recharge integration advertiser", "https://example.invalid/callback");
		jdbcTemplate.update(
				"INSERT INTO scx_ocpx_advertiser_account (advertiser_id,currency,available_balance) VALUES (?,'CNY',0)",
				ADVERTISER_ID);
	}

	@AfterEach
	void tearDown() {
		if (jdbcTemplate != null) {
			cleanup();
		}
	}

	@Test
	void replaysTheSameRechargeWithoutChangingBalanceOrLedger() {
		AdvertiserViews.RechargeResult posted = recharge("recharge-100", "100.000000");
		AdvertiserViews.RechargeResult replay = recharge("recharge-100", "100.000000");

		assertThat(posted.getAmount()).isEqualByComparingTo("100.000000");
		assertThat(posted.getBalanceAfter()).isEqualByComparingTo("100.000000");
		assertThat(replay.getEntryId()).isEqualTo(posted.getEntryId());
		assertThat(balance()).isEqualByComparingTo("100.000000");
		assertThat(ledgerCount()).isEqualTo(1L);
	}

	@Test
	void serializesConcurrentRechargeAgainstTheAdvertiserAccountRow() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<java.util.concurrent.Future<AdvertiserViews.RechargeResult>> futures = new ArrayList<>();
			for (int index = 0; index < 2; index++) {
				String requestId = "concurrent-recharge-" + index;
				futures.add(executor.submit(() -> {
					start.await();
					return recharge(requestId, "50.000000");
				}));
			}
			start.countDown();
			List<BigDecimal> balancesAfter = new ArrayList<>();
			for (var future : futures) {
				balancesAfter.add(future.get().getBalanceAfter());
			}
			assertThat(balancesAfter).containsExactlyInAnyOrder(new BigDecimal("50.000000"),
					new BigDecimal("100.000000"));
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(balance()).isEqualByComparingTo("100.000000");
		assertThat(ledgerCount()).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT SUM(amount) FROM scx_ocpx_billing_entry WHERE advertiser_id=?", BigDecimal.class,
				ADVERTISER_ID)).isEqualByComparingTo("100.000000");
	}

	private AdvertiserViews.RechargeResult recharge(String requestId, String amount) {
		return transaction.execute(status -> service.recharge(ADVERTISER_ID,
				new AdvertiserRequests.Recharge(requestId, new BigDecimal(amount), "integration test")));
	}

	private BigDecimal balance() {
		return jdbcTemplate.queryForObject(
				"SELECT available_balance FROM scx_ocpx_advertiser_account WHERE advertiser_id=?", BigDecimal.class,
				ADVERTISER_ID);
	}

	private Long ledgerCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=?",
				Long.class, ADVERTISER_ID);
	}

	private void cleanup() {
		jdbcTemplate.update("DELETE FROM scx_ocpx_billing_entry WHERE advertiser_id=?", ADVERTISER_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_advertiser_account WHERE advertiser_id=?", ADVERTISER_ID);
		jdbcTemplate.update("DELETE FROM scx_ocpx_advertiser WHERE id=?", ADVERTISER_ID);
	}

}
