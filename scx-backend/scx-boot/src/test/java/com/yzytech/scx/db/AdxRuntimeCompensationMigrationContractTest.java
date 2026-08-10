package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class AdxRuntimeCompensationMigrationContractTest {

	private static final String MIGRATION = "db/migration/V91__close_runtime_center_and_compensation.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void closesImmutableFactsRecoverableReplayAndCanonicalPermissions() throws Exception {
		String migration = resource(MIGRATION);
		assertThat(migration)
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_runtime_fact",
					"CREATE TABLE IF NOT EXISTS scx_adx_runtime_failure",
					"CREATE TABLE IF NOT EXISTS scx_adx_runtime_replay_request",
					"CREATE TABLE IF NOT EXISTS scx_adx_runtime_replay_attempt",
					"CREATE TABLE IF NOT EXISTS scx_adx_runtime_command_audit",
					"uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)",
					"mode VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL_SNAPSHOT_ONLY'", "trg_scx_adx_runtime_fact_no_update",
					"trg_scx_adx_runtime_failure_no_delete", "trg_scx_adx_runtime_replay_attempt_no_update",
					"adx:runtime:view", "adx:runtime:dlq:view", "adx:runtime:emergency-stop")
			.contains("WHERE rm.menu_id='perm_adx_runtime_read'", "DELETE FROM sys_role_menu")
			.doesNotContain("http://", "https://", "${SCX_", "raw_payload", "request_body", "response_body",
					"credential_ciphertext");
	}

	@Test
	void currentSchemaCarriesTheExactS10ForwardClosure() throws Exception {
		String migration = resource(MIGRATION);
		String current = resource(CURRENT_SCHEMA);
		assertThat(current).contains(migration);
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
