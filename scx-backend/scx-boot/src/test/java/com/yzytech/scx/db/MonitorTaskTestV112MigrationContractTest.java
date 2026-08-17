package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorTaskTestV112MigrationContractTest {

	private static final String MIGRATION = "db/migration/V112__add_monitor_task_test_permission.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationGrantsRealClickTestingOnlyToRoleAdmin() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("'ocpx:monitor-task:test'", "r.role_code='ROLE_ADMIN'", "r.role_code<>'ROLE_ADMIN'")
			.doesNotContain("scx_schema_migration", "role_advertiser");
	}

	@Test
	void currentSchemaCarriesMonitorTaskTestPermission() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("'ocpx:monitor-task:test'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
