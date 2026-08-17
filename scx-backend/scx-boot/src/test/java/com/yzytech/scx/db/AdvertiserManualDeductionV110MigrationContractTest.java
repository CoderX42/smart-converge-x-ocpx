package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdvertiserManualDeductionV110MigrationContractTest {

	private static final String MIGRATION = "db/migration/V110__add_advertiser_manual_deduction.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationExtendsTheImmutableLedgerAndGrantsOnlyTheSystemAdministrator() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("DROP CHECK ck_ocpx_billing_type", "entry_type IN ('RECHARGE','CLICK_CHARGE','MANUAL_DEBIT')",
					"'perm_ocpx_advertiser_deduct','扣减广告主余额'", "'ocpx:advertiser:deduct'", "r.role_code='ROLE_ADMIN'",
					"rm.role_id='role_advertiser'")
			.doesNotContain("scx_schema_migration");
	}

	@Test
	void currentSchemaKeepsManualDebitsNonnegativeAndOutOfTheAdvertiserRole() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql).contains("entry_type IN ('RECHARGE','CLICK_CHARGE','MANUAL_DEBIT')",
				"CONSTRAINT ck_ocpx_billing_amount CHECK (amount>=0)",
				"CONSTRAINT ck_ocpx_billing_balances CHECK (balance_before>=0 AND balance_after>=0)",
				"'perm_ocpx_advertiser_deduct','扣减广告主余额'", "'ocpx:advertiser:deduct'");
		String advertiserGrant = sql
			.substring(sql.lastIndexOf("DELETE FROM sys_role_menu\nWHERE role_id = 'role_advertiser';"));
		assertThat(advertiserGrant).doesNotContain("'perm_ocpx_advertiser_deduct'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
