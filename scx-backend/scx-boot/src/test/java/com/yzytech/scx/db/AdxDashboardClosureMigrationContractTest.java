package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class AdxDashboardClosureMigrationContractTest {

	private static final String MIGRATION = "db/migration/V90__close_dashboard_permission_contract.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsTheScopedFactAccessPathAndClosesTheCanonicalPermission() throws Exception {
		String migration = resource(MIGRATION);
		assertThat(migration)
			.contains("idx_scx_adx_dashboard_scope (advertiser_id,channel_id,media_account_id,time_hour)",
					"information_schema.statistics", "adx:dashboard:view", "perm_adx_dashboard_view_v90")
			.contains("WHERE rm.menu_id='perm_adx_dashboard_read'")
			.contains("UPDATE sys_menu SET del_flag='1'")
			.doesNotContain("CREATE TABLE", "adx:report:read", "${SCX_", "http://", "https://");
	}

	@Test
	void currentSchemaCarriesTheSameForwardPermissionClosure() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema)
			.contains("-- S9 Dashboard permission closure", "idx_scx_adx_dashboard_scope", "adx:dashboard:view")
			.contains("DELETE FROM sys_role_menu WHERE menu_id='perm_adx_dashboard_read'");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
