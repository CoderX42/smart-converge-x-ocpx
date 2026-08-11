package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OcpxTaskOperationalMigrationContractTest {

	private static final String MIGRATION = "db/migration/V86__add_task_operational_aggregate.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsTaskAggregateAndExplicitLegacyMonitorBoundary() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_task")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_task_change_audit")
			.contains("dedup_window_seconds BETWEEN 0 AND 86400")
			.contains("CONSTRAINT ck_scx_adx_task_rta CHECK (rta_mode='OFF')")
			.contains("ADD COLUMN task_mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED'")
			.contains("fk_scx_adx_monitor_task")
			.contains("task_config_revision>=1")
			.contains("adx:task:view", "adx:task:add", "adx:task:edit", "adx:task:status", "adx:task:archive");
		assertThat(migration).contains("Saving a Task never publishes a route")
			.doesNotContain("UPDATE scx_adx_monitor_link SET task_id", "INSERT INTO scx_ocpx_config_outbox",
					"INSERT IGNORE INTO scx_ocpx_config_outbox", "CREATE TABLE IF NOT EXISTS scx_adx_event_layer",
					"TRUNCATE TABLE", "DROP TABLE", "${SCX_");
	}

	@Test
	void currentSchemaAndVerifierCarryTheSameContract() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema).contains("-- V86 current state: Task operational aggregate")
			.contains("uk_scx_ocpx_task_name (advertiser_id,current_name_key)")
			.contains("ck_scx_ocpx_task_exposure")
			.contains("idx_scx_ocpx_monitor_task")
			.contains("perm_ocpx_task_status_v86");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
