package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxAdminCrudRbacMigrationContractTest {

	private static final String MIGRATION = "db/migration/V104__grant_admin_full_ocpx_crud.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void forwardMigrationAddsOnlyMissingAdminDeletePermissions() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("perm_ocpx_advertiser_delete", "ocpx:advertiser:delete", "perm_ocpx_media_delete",
					"ocpx:media:delete", "r.role_code='ROLE_ADMIN'", "INSERT IGNORE INTO sys_role_menu")
			.doesNotContain("role_advertiser", "scx_schema_migration");
	}

	@Test
	void currentSchemaSeedsDeletePermissionsBeforeGrantingAllOcpxMenusToAdmin() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql).contains("'perm_ocpx_advertiser_delete'", "'ocpx:advertiser:delete'",
				"'perm_ocpx_media_delete'", "'ocpx:media:delete'",
				"FROM sys_role r JOIN sys_menu m ON (m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%')",
				"WHERE r.role_code='ROLE_ADMIN'");
		String advertiserGrant = sql
			.substring(sql.lastIndexOf("DELETE FROM sys_role_menu\nWHERE role_id = 'role_advertiser';"));
		assertThat(advertiserGrant).doesNotContain("perm_ocpx_advertiser_delete", "perm_ocpx_media_delete");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
