package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdxMediaAccountMigrationContractTest {

	private static final String MIGRATION = "db/migration/V84__add_media_account_credentials.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsSeparatedAccountCredentialOwnerAndRouteContracts() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_media_account ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_credential ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_credential_revision ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_account_owner ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_account_change_audit ")
			.contains("UNIQUE KEY uk_scx_adx_media_credential_identifier")
			.contains("login_identifier_ciphertext TEXT NOT NULL")
			.contains("secret_ciphertext TEXT NOT NULL")
			.contains("published_media_credential_secret_revision BIGINT NOT NULL DEFAULT 0")
			.contains("adx:media-account:credential:rotate", "adx:media-account:owner:edit", "adx:media-account:debug");
		assertThat(migration).contains("Existing Monitor rows are intentionally not")
			.doesNotContain("UPDATE scx_adx_monitor_link SET media_account_id")
			.doesNotContain("DROP TABLE", "TRUNCATE TABLE", "${SCX_", "password VARCHAR");
	}

	@Test
	void currentSchemaCarriesTheSameMediaAccountContractWithoutBackfill() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("-- V84 current state: Channel/MediaAccount separation")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_account ")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_media_credential_revision ")
			.contains("published_media_account_credential_revision BIGINT NOT NULL DEFAULT 0")
			.contains("perm_adx_media_credential_rotate")
			.doesNotContain("UPDATE scx_adx_monitor_link SET media_account_id");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
