package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxUserScopeMigrationContractTest {

	private static final String MIGRATION = "db/migration/V81__add_adx_user_object_scopes.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void addsExplicitVersionedScopesAndAdminPermissionsWithoutDestructiveBackfill() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");
		assertThat(normalized).contains("CREATE TABLE IF NOT EXISTS scx_adx_user_scope_revision")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_user_scope")
			.contains("UNIQUE KEY uk_scx_adx_user_scope")
			.contains("'ALL',NULL,'MANAGE'")
			.contains("u.id,'ADVERTISER',u.shop_id, 'MANAGE'")
			.contains("'CHANNEL',m.channel_id")
			.contains("JOIN scx_adx_user_scope_revision v ON v.user_id=u.id AND v.version=1")
			.contains("adx:scope:view", "adx:scope:edit", "ROLE_ADMIN");
		assertThat(migration)
			.doesNotContain("DROP TABLE", "TRUNCATE TABLE", "DELETE FROM sys_user", "${SCX_");
	}

	@Test
	void currentSchemaCarriesTheSameScopeTablesAndPermissionSeed() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("-- V81 current state: explicit OCPX object scopes")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope_revision")
			.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope")
			.contains("perm_ocpx_scope_view", "perm_ocpx_scope_edit");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("migration resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
