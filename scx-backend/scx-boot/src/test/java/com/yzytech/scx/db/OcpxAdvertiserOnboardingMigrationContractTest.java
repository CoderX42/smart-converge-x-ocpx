package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxAdvertiserOnboardingMigrationContractTest {

	private static final String MIGRATION = "db/migration/V82__close_advertiser_onboarding_materials.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsWriteOnlyCredentialDocumentAndRepublishContracts() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_integration_document")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_advertiser_credential_revision")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_advertiser_change_audit")
			.contains("secret_ciphertext TEXT NULL COMMENT 'AES-GCM payload encrypted by existing ProtocolCrypto'")
			.contains("republish_required TINYINT(1) NOT NULL DEFAULT 0")
			.contains("published_advertiser_credential_revision BIGINT NOT NULL DEFAULT 0")
			.contains("adx:advertiser:credential", "adx:advertiser:document:edit");
		assertThat(migration).contains("storage_mode,status,reason,created_by")
			.doesNotContain("INSERT INTO scx_adx_advertiser_credential_revision SELECT secret_key")
			.doesNotContain("DROP TABLE", "TRUNCATE TABLE", "${SCX_");
	}

	@Test
	void currentSchemaCarriesTheSameCurrentStateAndNeverSeedsSecrets() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("-- V82 current state: Advertiser onboarding")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_integration_document")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_credential_revision")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_change_audit")
			.contains("capability_revision BIGINT NOT NULL DEFAULT 1")
			.contains("perm_ocpx_advertiser_credential", "perm_ocpx_advertiser_document_edit")
			.doesNotContain("INSERT INTO scx_ocpx_advertiser_credential_revision SELECT secret_key");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
