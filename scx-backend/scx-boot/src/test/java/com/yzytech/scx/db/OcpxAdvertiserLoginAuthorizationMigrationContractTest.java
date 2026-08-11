package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxAdvertiserLoginAuthorizationMigrationContractTest {

	private static final String MIGRATION = "db/migration/V95__add_advertiser_login_authorization.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsOptionalIntegrationAndUnambiguousLoginBindingWithoutLedgerDependency() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("MODIFY COLUMN adapter_code VARCHAR(32) NULL DEFAULT NULL",
					"MODIFY COLUMN bid_url VARCHAR(5000) NULL", "MODIFY COLUMN password_hash VARCHAR(100) NULL",
					"CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_login_binding",
					"UNIQUE KEY uk_scx_ocpx_advertiser_login_user (user_id)", "HAVING COUNT(*)=1",
					"u.type='2' AND u.del_flag='0'", "ocpx:advertiser:grant", "r.role_code='ROLE_ADMIN'")
			.doesNotContain("FROM scx_schema_migration", "INSERT INTO scx_ocpx_advertiser_login_binding SELECT *",
					"password_hash=", "FOREIGN KEY", "REFERENCES scx_ocpx_advertiser", "REFERENCES sys_user",
					"REFERENCES sys_role", "DROP TABLE", "TRUNCATE TABLE");
	}

	@Test
	void baselineManifestAndVerifierCarryTheAuthorizationContract() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		String manifest = workspace("db/smart-converge-x/schema-required-tables.sh");
		String verifier = workspace("db/smart-converge-x/verify-database.sh");

		assertThat(schema).contains("-- V95 current state:",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_login_binding", "ocpx:advertiser:grant");
		assertThat(manifest).contains("scx_ocpx_advertiser_login_binding");
		assertThat(verifier).contains("advertiser_login_ambiguous_count", "advertiser_login_contract_violation_count",
				"LEFT JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY b.advertiser_id",
				"advertiser_grant_non_admin_count");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String workspace(String path) throws Exception {
		return java.nio.file.Files.readString(java.nio.file.Path.of("..", path), StandardCharsets.UTF_8);
	}

}
