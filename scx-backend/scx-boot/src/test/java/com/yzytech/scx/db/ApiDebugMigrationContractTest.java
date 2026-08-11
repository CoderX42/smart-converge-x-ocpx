package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDebugMigrationContractTest {

	private static final String MIGRATION = "db/schema/scx_current_schema.sql";

	@Test
	void packagesDebuggerStorageCorrelationAndAdminOnlyPermissions() throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			String debuggerRbac = sql.substring(sql.indexOf("('menu_ocpx_api_debug'"),
					sql.indexOf("-- Forward-only OCPX click-deduplication foundation."));
			assertThat(sql).contains("scx_api_debug_case", "scx_api_debug_execution", "scx_api_debug_execution_log",
					"scx_api_debug_log_access_audit", "scx_ocpx_delivery_attempt", "debug_execution_id",
					"ocpx:api-debug:execute-billable", "ocpx:api-debug:logs-raw", "WHERE r.role_code='ROLE_ADMIN'");
			assertThat(debuggerRbac).doesNotContain("ROLE_OPERATOR");
			assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM scx_ocpx_billing_entry");
		}
	}

}
