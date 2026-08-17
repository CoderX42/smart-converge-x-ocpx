package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CreativeConfigurableBillingV111MigrationContractTest {

	private static final String MIGRATION = "db/migration/V111__add_creative_and_configurable_billing.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationAddsCreativeOwnershipAndFrozenBillingTruth() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("V111 blocked: active monitor tasks require a positive unit_price before CPC backfill",
					"CREATE TABLE scx_ocpx_creative", "CREATE TABLE scx_ocpx_creative_promotion_link",
					"FOREIGN KEY (advertiser_id,creative_id)", "FOREIGN KEY (advertiser_id,promotion_link_id)",
					"CREATE TABLE scx_ocpx_billing_config_audit", "before_config JSON NULL",
					"after_config JSON NOT NULL", "ADD COLUMN billing_enabled", "ADD COLUMN billing_type",
					"billing_enabled_snapshot", "billing_type_snapshot", "billing_unit_price_snapshot",
					"conversion_event_snapshot", "billing_reason_code", "CONVERSION_CHARGE",
					"rejected_conversion_count", "ocpx:creative:bind", "ocpx:billing-entry:view")
			.doesNotContain("scx_schema_migration", "ALTER TABLE scx_ocpx_creative ADD COLUMN c_id");
	}

	@Test
	void currentSchemaCarriesTheSameCreativeAndBillingContract() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql)
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_creative",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_creative_promotion_link",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_billing_config_audit", "billing_enabled TINYINT(1)",
					"billing_type TINYINT UNSIGNED", "billing_enabled_snapshot TINYINT(1) NULL",
					"billing_type_snapshot TINYINT UNSIGNED NULL", "billing_unit_price_snapshot DECIMAL(20,6) NULL",
					"conversion_event_snapshot VARCHAR(32) NULL", "rejected_conversion_count BIGINT",
					"entry_type IN ('RECHARGE','MANUAL_DEBIT','CLICK_CHARGE','CONVERSION_CHARGE')",
					"'ocpx:creative:add'", "'ocpx:billing-entry:view'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
