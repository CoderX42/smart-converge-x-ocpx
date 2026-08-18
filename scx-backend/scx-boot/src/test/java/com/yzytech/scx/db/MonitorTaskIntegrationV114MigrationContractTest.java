package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorTaskIntegrationV114MigrationContractTest {

	private static final String MIGRATION = "db/migration/V114__add_monitor_task_integration_permission.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationGrantsMediaIntegrationOnlyToRoleAdmin() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("'ocpx:monitor-task:integration'", "r.role_code='ROLE_ADMIN'", "r.role_code<>'ROLE_ADMIN'")
			.doesNotContain("scx_schema_migration", "role_advertiser");
	}

	@Test
	void currentSchemaCarriesMonitorTaskIntegrationPermission() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("'ocpx:monitor-task:integration'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
