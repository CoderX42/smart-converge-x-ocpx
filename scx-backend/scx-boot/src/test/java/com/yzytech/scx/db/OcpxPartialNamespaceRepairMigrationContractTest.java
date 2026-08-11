package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class OcpxPartialNamespaceRepairMigrationContractTest {

	private static final String MIGRATION = "db/migration/V93__repair_partial_ocpx_namespace.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void failsClosedUnlessTheTableAndMetadataNamespaceIsAlreadyCanonical() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("CREATE PROCEDURE scx_v93_preflight()", "table_name LIKE 'scx_adx_%'",
					"V92 OCPX table phase to be complete", "LOWER(index_name) LIKE '%adx%'",
					"LOWER(constraint_name) LIKE '%adx%'", "LOWER(trigger_name) LIKE '%adx%'",
					"LOWER(table_comment) LIKE '%adx%'", "target_menu.del_flag<>'1'",
					"active OCPX menu conflicting with an ADX source menu")
			.doesNotContain("TRUNCATE TABLE", "DROP TABLE ", "${SCX_");
	}

	@Test
	void restoresExplicitScopesAndCanonicalMenuAssignmentsWithoutReplayingHistory() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope_revision",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope", "UNIQUE KEY uk_scx_ocpx_user_scope",
					"'V93_REPAIR'", "'ALL',NULL,'MANAGE'", "u.id,'ADVERTISER',u.shop_id",
					"'CHANNEL',m.channel_id", "perm_ocpx_scope_view", "perm_ocpx_scope_edit",
					"DELETE source_mapping", "target_mapping.menu_id=mapping.new_name", "DELETE target_menu",
					"WHERE target_menu.del_flag='1'", "SET role_mapping.menu_id=mapping.new_name",
					"SET source_menu.id=mapping.new_name", "CREATE PROCEDURE scx_v93_verify()",
					"dangling OCPX role-menu assignments")
			.doesNotContain("DELETE FROM scx_", "credential_ciphertext=", "secret_ciphertext=", "password=");
	}

	@Test
	void currentSchemaAndVerifierAlreadyDescribeTheRepairedFinalState() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		String verifier = resourceFromWorkspace("db/smart-converge-x/verify-database.sh");

		assertThat(schema).contains("CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope_revision",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope", "menu_ocpx_home", "perm_ocpx_scope_view",
				"perm_ocpx_scope_edit");
		assertThat(schema.toLowerCase()).doesNotContain("adx");
		assertThat(verifier).contains("retired table or menu namespace remains",
				"dangling OCPX role-menu assignments found", "administrator OCPX menu grants are incomplete");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("migration resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String resourceFromWorkspace(String path) throws Exception {
		return java.nio.file.Files.readString(java.nio.file.Path.of("..", path), StandardCharsets.UTF_8);
	}

}
