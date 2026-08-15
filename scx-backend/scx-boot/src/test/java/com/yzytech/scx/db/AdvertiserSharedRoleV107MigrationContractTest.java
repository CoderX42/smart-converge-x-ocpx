package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdvertiserSharedRoleV107MigrationContractTest {

	private static final String MIGRATION = "db/migration/V107__add_advertiser_shared_role_permissions.sql";

	@Test
	void migrationSeedsAdminManagementAndFullSharedAdvertiserRole() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("SIGNAL SQLSTATE '45000'", "ocpx:advertiser:role:manage", "ocpx:advertiser:scope:all",
					"广告主自助账号共享可配置角色", "exactly one active ROLE_ADMIN is required",
					"r.role_code='ROLE_ADMIN'", "r.role_code<>'ROLE_ADMIN'",
					"DELETE FROM sys_role_menu\nWHERE role_id='role_advertiser'",
					"m.path LIKE '/ocpx%'", "m.permission LIKE 'ocpx:%'",
					"COALESCE(m.permission,'') <> 'ocpx:advertiser:role:manage'")
			.doesNotContain("scx_schema_migration");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
