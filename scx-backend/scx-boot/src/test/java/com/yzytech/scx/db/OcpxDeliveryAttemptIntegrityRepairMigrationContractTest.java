package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class OcpxDeliveryAttemptIntegrityRepairMigrationContractTest {

	private static final String MIGRATION = "db/migration/V94__repair_delivery_attempt_integrity.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void preservesEveryAttemptAndRenumbersOnlyDuplicateJobSequences() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("GET_LOCK('scx_v94_delivery_attempt_integrity',30)",
					"DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci",
					"COUNT(*)<>COUNT(DISTINCT attempt_no)",
					"ROW_NUMBER() OVER (", "ORDER BY attempt_row.create_time,attempt_row.id",
					"job_row.status='PROCESSING'", "job_row.lease_until>=NOW(3)",
					"ADD COLUMN original_attempt_no INT NULL",
					"attempt_row.original_attempt_no=IF(",
					"attempt_row.attempt_no=repair.repaired_attempt_no",
					"job_row.attempt_count=repair.repaired_max",
					"ADD UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)")
			.doesNotContain("DELETE FROM scx_ocpx_delivery_attempt",
					"DELETE FROM scx_ocpx_delivery_job", "TRUNCATE TABLE", "DROP TABLE ");
	}

	@Test
	void failsClosedAndVerifiesTheCanonicalFinalInvariant() throws Exception {
		String migration = resource(MIGRATION);

		assertThat(migration)
			.contains("CREATE PROCEDURE scx_v94_preflight()",
					"incompatible delivery-attempt index with the canonical name",
					"refuses to renumber an active or leased delivery job",
					"CREATE PROCEDURE scx_v94_verify()", "left duplicate delivery attempt numbers",
					"index_columns<>'job_id,attempt_no'",
					"delivery job attempt count verification failed",
					"RELEASE_LOCK('scx_v94_delivery_attempt_integrity')");
	}

	@Test
	void baselineAndVerifierCarryTheRepairedContract() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		String verifier = resourceFromWorkspace("db/smart-converge-x/verify-database.sh");

		assertThat(schema).contains("original_attempt_no INT NULL",
				"UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)");
		assertThat(verifier).contains("s10_delivery_attempt_unique_columns",
				"s10_duplicate_delivery_attempt_count", "s10_delivery_attempt_count_mismatch",
				"job_id,attempt_no");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("migration resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String resourceFromWorkspace(String path) throws Exception {
		return java.nio.file.Files.readString(java.nio.file.Path.of("..", path), StandardCharsets.UTF_8);
	}

}
