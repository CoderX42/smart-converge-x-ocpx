package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OcpxAppLinkStructureMigrationContractTest {

	private static final String MIGRATION = "db/migration/V85__structure_app_link_master_data.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsStructuredLinkAuditAndImmutableRouteSnapshotContract() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_app_link_change_audit")
			.contains("mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PENDING_PRODUCT'")
			.contains("ADD COLUMN link_url_template VARCHAR(5000) NULL")
			.contains("ADD COLUMN template_macro_set JSON NULL")
			.contains("UNIQUE KEY uk_scx_adx_app_link_name (product_id,normalized_name,current_name_marker)")
			.contains("published_app_link_version BIGINT NOT NULL DEFAULT 0")
			.contains("published_link_url_template TEXT NULL")
			.contains("published_redirect_enabled TINYINT(1) NOT NULL DEFAULT 0")
			.contains("adx:app-link:view", "adx:app-link:add", "adx:app-link:edit", "adx:app-link:archive",
					"adx:app-link:validate");
		assertThat(migration).contains("never promotes a legacy URL into a validated URL")
			.doesNotContain("UPDATE scx_adx_app_link SET", "TRUNCATE TABLE", "DROP TABLE", "${SCX_");
	}

	@Test
	void currentSchemaCarriesTheSameExplicitMappingContract() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema).contains("-- V85 current state: structured AppLink")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_app_link_change_audit")
			.contains("mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PENDING_PRODUCT'")
			.contains("published_link_template_fingerprint CHAR(64) NULL")
			.contains("idx_scx_ocpx_monitor_app_link_publish")
			.contains("perm_ocpx_app_link_validate_v85");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
