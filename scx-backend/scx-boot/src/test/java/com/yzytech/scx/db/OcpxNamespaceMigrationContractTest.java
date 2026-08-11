package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OcpxNamespaceMigrationContractTest {

	private static final String MIGRATION = "db/migration/V92__canonicalize_ocpx_management_namespace.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void renamesEveryManagementTableOnlyAfterACompletePreflight() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(occurrences(migration, "('scx_adx_")).isEqualTo(45);
		assertThat(migration)
			.contains("CREATE PROCEDURE scx_v92_preflight_tables()", "each mapping must have exactly one source or target table",
					"CALL scx_v92_preflight_tables()", "CREATE PROCEDURE scx_v92_rename_tables()",
					"RENAME TABLE `", "CREATE PROCEDURE scx_v92_rename_indexes()",
					"CREATE PROCEDURE scx_v92_rename_foreign_keys()", "CREATE PROCEDURE scx_v92_rename_checks()",
					"CREATE TRIGGER trg_scx_ocpx_route_event_snapshot_no_update",
					"CREATE TRIGGER trg_scx_ocpx_runtime_command_audit_no_delete")
			.doesNotContain("TRUNCATE TABLE", "DROP TABLE ", "DELETE FROM scx_", "${SCX_", "http://", "https://");
	}

	@Test
	void canonicalizesMenusPermissionsAndMetadataWithoutCredentialRewrites() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("CREATE PROCEDURE scx_v92_preflight_menus()", "target OCPX menu id already exists",
					"REPLACE(path,'/adx','/ocpx')", "REPLACE(component,'adx/','ocpx/')",
					"REPLACE(permission,'adx:','ocpx:')", "CREATE PROCEDURE scx_v92_update_table_comments()")
			.doesNotContain("credential_ciphertext=", "secret_ciphertext=", "access_token=", "password=");
	}

	@Test
	void currentSchemaContainsOnlyTheCanonicalOcpxNamespace() throws Exception {
		String schema = resource(CURRENT_SCHEMA);

		assertThat(schema).contains("scx_ocpx_advertiser", "scx_ocpx_runtime_fact", "menu_ocpx_home",
				"ocpx:dashboard:view");
		assertThat(schema.toLowerCase()).doesNotContain("adx");
	}

	private int occurrences(String text, String needle) {
		return (text.length() - text.replace(needle, "").length()) / needle.length();
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
