package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AdvertiserAccountRbacMigrationContractTest {

	private static final String MIGRATION = "db/migration/V103__add_advertiser_account_scope.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	private static final String[] ADVERTISER_MENU_IDS = { "menu_ocpx_home", "menu_ocpx_advertiser",
			"menu_ocpx_promotion_link", "menu_ocpx_monitor_task", "menu_ocpx_report", "menu_ocpx_flow_log",
			"perm_ocpx_report_export" };

	@Test
	void forwardMigrationAddsAccountBindingAndGeneratedUrlCapacity() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("ADD COLUMN account_user_id VARCHAR(64) NULL", "uk_ocpx_advertiser_account_user",
					"FOREIGN KEY (account_user_id) REFERENCES sys_user(id)", "MODIFY COLUMN show_url TEXT NULL",
					"MODIFY COLUMN clk_url TEXT NULL", "ROLE_ADVERTISER", "type='0'", "shop_id=NULL",
					"SIGNAL SQLSTATE '45000'", "'ocpx:report:export'")
			.doesNotContain("scx_schema_migration");
		assertExactAdvertiserRoleGrant(sql);
	}

	@Test
	void currentSchemaSeedsTheSameProtectedReadOnlyRole() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql).contains("account_user_id VARCHAR(64) NULL", "uk_ocpx_advertiser_account_user",
				"FOREIGN KEY (account_user_id) REFERENCES sys_user(id)", "show_url TEXT NULL", "clk_url TEXT NULL",
				"'role_advertiser','广告主','ROLE_ADVERTISER'", "'ocpx:report:export'");
		assertExactAdvertiserRoleGrant(sql);
	}

	private void assertExactAdvertiserRoleGrant(String sql) {
		int grantStart = sql.lastIndexOf("DELETE FROM sys_role_menu\nWHERE role_id = 'role_advertiser';");
		assertThat(grantStart).isNotNegative();
		String grant = sql.substring(grantStart);
		assertThat(grant).contains(ADVERTISER_MENU_IDS)
			.doesNotContain("menu_ocpx_media", "menu_ocpx_deduction_rule", "perm_ocpx_advertiser_add",
					"perm_ocpx_advertiser_edit", "perm_ocpx_advertiser_recharge", "perm_ocpx_advertiser_delete",
					"perm_ocpx_promotion_link_add", "perm_ocpx_promotion_link_edit", "perm_ocpx_promotion_link_delete",
					"perm_ocpx_monitor_task_add", "perm_ocpx_monitor_task_edit", "perm_ocpx_monitor_task_delete",
					"perm_ocpx_media_delete");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
