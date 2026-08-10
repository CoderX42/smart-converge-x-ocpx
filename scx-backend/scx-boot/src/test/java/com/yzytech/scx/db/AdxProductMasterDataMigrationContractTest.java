package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdxProductMasterDataMigrationContractTest {

	private static final String MIGRATION = "db/migration/V83__add_product_master_data.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsIndependentProductPolicyStorageAndRouteContracts() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_product ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product_logo_object")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product_event_policy")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product_change_audit")
			.contains("UNIQUE KEY uk_scx_adx_product_code (advertiser_id,code)")
			.contains("UNIQUE KEY uk_scx_adx_product_name (advertiser_id,normalized_name)")
			.contains("UNIQUE KEY uk_scx_adx_product_event (product_id,event_definition_id)")
			.contains("ADD COLUMN product_id VARCHAR(32) NULL")
			.contains("published_product_event_policy_revision BIGINT NOT NULL DEFAULT 0")
			.contains("adx:product:view", "adx:product:add", "adx:product:edit", "adx:product:archive");
		assertThat(migration).contains("Existing AppLinks are intentionally not")
			.doesNotContain("UPDATE scx_adx_app_link SET product_id")
			.doesNotContain("DROP TABLE", "TRUNCATE TABLE", "${SCX_");
	}

	@Test
	void currentSchemaCarriesTheSameProductContractWithoutBackfill() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("-- V83 current state: independent Product aggregate")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product_logo_object")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_product_event_policy")
			.contains("published_product_event_policy_revision BIGINT NOT NULL DEFAULT 0")
			.contains("perm_adx_product_archive")
			.doesNotContain("UPDATE scx_adx_app_link SET product_id");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
