package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OcpxEventModelClosureMigrationContractTest {

	private static final String MIGRATION = "db/migration/V87__close_event_model_truth.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsFourLayerTruthWithoutGuessingOrPublishing() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("mapping_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNMAPPED'")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_task_event_binding")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_event_change_audit")
			.contains("snapshot_schema_version INT NOT NULL DEFAULT 1")
			.contains("snapshot_mode VARCHAR(24) NOT NULL DEFAULT 'LEGACY_V80'")
			.contains("direction IS NOT NULL AND standard_type IS NOT NULL AND subject_type IS NOT NULL")
			.contains("external_code IS NOT NULL")
			.contains("callback_ratio_basis_points IS NOT NULL")
			.contains("callback_ratio_basis_points BETWEEN 0 AND 10000")
			.contains("limit_window='NATURAL_DAY' AND limit_timezone='Asia/Shanghai'")
			.contains("sampling_algorithm='SHA256_MOD_10000_V1'")
			.contains("trg_scx_adx_route_event_snapshot_no_update")
			.contains("trg_scx_adx_route_event_snapshot_no_delete")
			.contains("adx:event:view", "adx:event-mapping:view", "adx:task:event:edit");
		assertThat(migration).contains("No business mapping is", "inferred from names, external codes")
			.doesNotContain("INSERT INTO scx_ocpx_config_outbox", "INSERT IGNORE INTO scx_ocpx_config_outbox",
					"UPDATE scx_adx_event_definition SET event_code", "TRUNCATE TABLE", "DROP TABLE", "${SCX_");
	}

	@Test
	void currentSchemaVerifierAndManifestCarryS6Contract() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema).contains("CREATE TABLE IF NOT EXISTS scx_ocpx_task_event_binding")
			.contains("uk_scx_ocpx_route_event_canonical")
			.contains("event_mapping_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED'")
			.contains("RouteEventSnapshot is immutable; publish a new version")
			.contains("perm_ocpx_task_event_edit_v87");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
