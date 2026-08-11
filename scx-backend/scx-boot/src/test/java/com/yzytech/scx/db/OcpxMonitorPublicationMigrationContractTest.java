package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxMonitorPublicationMigrationContractTest {

	private static final String MIGRATION = "db/migration/V88__add_monitor_publication_runtime_dimensions.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void modelsImmutablePublicationAndAckBasedRuntimeWithoutLegacyGuessing() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_route_version")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_route_publish_record")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_route_distribution")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_route_instance_ack")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_route_runtime_state")
			.contains("uk_scx_adx_route_publish_request (publish_request_id)")
			.contains("uk_scx_adx_route_code_hash (route_code_hash)")
			.contains("trg_scx_adx_route_version_no_update")
			.contains("status='PUBLISHED'")
			.contains("rta_mode='OFF'")
			.contains("delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'")
			.contains("trackingMacroSchemaVersion", "trackingMacroPairs", "adx:monitor:copy");
		assertThat(migration).contains("Legacy Monitor rows remain unmapped")
			.doesNotContain("UPDATE scx_adx_monitor_link SET task_id", "${SCX_", "http://", "TRUNCATE TABLE",
					"DROP TABLE", "DELETE FROM scx_adx_route_version");
	}

	@Test
	void currentSchemaCarriesTheSameS7TruthAndFailClosedPermissions() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema).contains("CREATE TABLE IF NOT EXISTS scx_ocpx_route_version")
			.contains("RouteVersion is immutable; publish a new version")
			.contains(
					"acceptance_status IN ('PENDING_DISTRIBUTION','ACTIVE','OVERLAP','SUPERSEDED','DENIED','EXPIRED')")
			.contains("perm_ocpx_task_publish_v88", "perm_ocpx_monitor_status_v88");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
