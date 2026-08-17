package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TruthfulDashboardBillingV108MigrationContractTest {

	private static final String MIGRATION = "db/migration/V108__add_truthful_dashboard_billing.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationCreatesAuthoritativeFactsLedgerAccountAndHourlyProjection() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("V108 blocked: legacy report rows require an explicit audited migration plan",
					"CREATE TABLE scx_ocpx_advertiser_account", "available_balance DECIMAL(20,6)",
					"CREATE TABLE scx_ocpx_event_fact", "UNIQUE KEY uk_ocpx_event_fact_key (fact_key)",
					"UNIQUE KEY uk_ocpx_event_fact_identity",
					"CREATE TABLE scx_ocpx_billing_entry", "UNIQUE KEY uk_ocpx_billing_business_key (business_key)",
					"CREATE TABLE scx_ocpx_hourly_metric", "UNIQUE KEY uk_ocpx_hourly_metric_scope",
					"billing_status VARCHAR(40) NOT NULL DEFAULT 'NOT_APPLICABLE'",
					"MODIFY COLUMN unit_price DECIMAL(20,6) NOT NULL DEFAULT 0",
					"conversion_event VARCHAR(32) NOT NULL DEFAULT 'ACTIVATE'", "ocpx:monitor-task:billing",
					"m.permission IN ('ocpx:advertiser:recharge','ocpx:monitor-task:billing')")
			.doesNotContain("scx_schema_migration");
	}

	@Test
	void currentSchemaKeepsTheSameTruthAndExcludesFinanceFromTheSharedAdvertiserRole() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql)
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_account",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_event_fact",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_billing_entry",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_metric", "target_conversion_count BIGINT",
					"rejected_click_count BIGINT", "'ocpx:monitor-task:billing'");
		String advertiserGrant = sql.substring(sql.lastIndexOf("DELETE FROM sys_role_menu\nWHERE role_id = 'role_advertiser';"));
		assertThat(advertiserGrant).doesNotContain("'perm_ocpx_advertiser_recharge'",
				"'perm_ocpx_advertiser_deduct'", "'perm_ocpx_monitor_task_billing'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
