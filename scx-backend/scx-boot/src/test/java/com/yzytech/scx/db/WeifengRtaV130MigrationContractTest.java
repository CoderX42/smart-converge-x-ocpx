package com.yzytech.scx.db;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Static guard for the forward-only V130 RTA adapter constraint change. */
class WeifengRtaV130MigrationContractTest {

	private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V130__add_weifeng_rta_adapter.sql");

	private static final Path CURRENT_SCHEMA = Path.of("src/main/resources/db/schema/scx_current_schema.sql");

	@Test
	void migrationOnlyWidensTheProfileAdapterCheck() throws Exception {
		String sql = Files.readString(MIGRATION);

		assertThat(sql).contains("V130", "scx_ocpx_rta_profile", "DROP CHECK ck_ocpx_rta_profile_adapter",
				"adapter_code IN ('MOCK','WEIFENG_RTA')");
		assertThat(sql).doesNotContain("V128", "V129", "CREATE TABLE", "DROP TABLE", "INSERT INTO",
				"scx_schema_migration");
	}

	@Test
	void currentSchemaRetiresTheLegacyProfileTable() throws Exception {
		String schema = Files.readString(CURRENT_SCHEMA);

		assertThat(schema).doesNotContain("CREATE TABLE IF NOT EXISTS scx_ocpx_rta_profile");
		assertThat(schema).contains("CREATE TABLE IF NOT EXISTS scx_ocpx_rta_upstream");
	}

}
