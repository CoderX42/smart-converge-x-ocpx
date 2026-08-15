package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RetiredOcpxPermissionsV109MigrationContractTest {

	private static final String MIGRATION = "db/migration/V109__retire_legacy_ocpx_permissions.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	private static final String[] RETIRED_PERMISSIONS = { "ocpx:api-debug:read", "ocpx:monitor:publish",
			"ocpx:monitor:read", "ocpx:monitor:write" };

	@Test
	void migrationRemovesRoleMappingsBeforeSoftDeletingLegacyPermissions() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql).contains(RETIRED_PERMISSIONS)
			.contains("DELETE rm\nFROM sys_role_menu rm\nJOIN sys_menu m ON m.id=rm.menu_id",
					"UPDATE sys_menu\nSET del_flag='1',update_time=NOW()", "START TRANSACTION;", "COMMIT;")
			.doesNotContain("DELETE FROM sys_menu", "scx_schema_migration");
		assertThat(sql.indexOf("DELETE rm")).isLessThan(sql.indexOf("UPDATE sys_menu"));
	}

	@Test
	void currentSchemaDefensivelyRetiresTheSameLegacyPermissions() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql).contains(RETIRED_PERMISSIONS)
			.contains("DELETE rm\nFROM sys_role_menu rm\nJOIN sys_menu m ON m.id=rm.menu_id",
					"UPDATE sys_menu\nSET del_flag='1',update_time=NOW()");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
