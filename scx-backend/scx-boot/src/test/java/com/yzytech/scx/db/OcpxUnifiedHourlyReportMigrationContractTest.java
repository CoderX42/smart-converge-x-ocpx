package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OcpxUnifiedHourlyReportMigrationContractTest {

	private static final String MIGRATION = "db/migration/V89__unify_hourly_facts_and_reports.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void evolvesOneHourlyTruthWithFrozenDimensionsAndIdempotentJobs() throws Exception {
		String migration = resource(MIGRATION);
		String normalized = migration.replaceAll("\\s+", " ");

		assertThat(normalized).contains("ADD COLUMN dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED'")
			.contains("ADD UNIQUE KEY uk_scx_adx_hourly_frozen_grain (time_hour,dimension_key)")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_hourly_fact_contribution")
			.contains("UNIQUE KEY uk_scx_adx_fact_contribution_source (source_event_id,contribution_kind)")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_hourly_aggregation_cursor")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_hourly_adjustment")
			.contains("CREATE TABLE IF NOT EXISTS scx_adx_report_export_task")
			.contains("user_id,scope_hash,query_hash,export_type,idempotency_bucket")
			.contains("trg_scx_adx_hourly_adjustment_no_update")
			.contains("adx:report:view", "adx:report:deduction:export", "adx:report:metric:sensitive");
		assertThat(migration).contains("never infers")
			.doesNotContain("UPDATE scx_adx_hourly_report r JOIN scx_adx_task", "TRUNCATE TABLE", "DROP TABLE",
					"${SCX_", "http://", "https://");
	}

	@Test
	void currentSchemaCarriesTheSameS8Truth() throws Exception {
		String schema = resource(CURRENT_SCHEMA);
		assertThat(schema).contains("-- S8 unified hourly facts and reports")
			.contains("uk_scx_ocpx_hourly_frozen_grain")
			.contains("metric_definitions_version VARCHAR(32) NOT NULL DEFAULT 'S8_V1'")
			.contains("scx_ocpx_report_export_audit")
			.contains("Hourly adjustment facts are immutable");
	}

	@Test
	void reportSourceKeepsWorkersFailClosedAndStorageReferencesInternal() throws Exception {
		String application = resource("application.yml");
		String exports = source(
				"scx-ocpx-admin/src/main/java/com/yzytech/scx/ocpx/admin/service/OcpxReportExportService.java");
		String views = source(
				"scx-ocpx-admin/src/main/java/com/yzytech/scx/ocpx/admin/dto/UnifiedReportViews.java");
		assertThat(application).contains("aggregation-worker-enabled: false", "export-worker-enabled: false",
				"maximum-export-rows: 100000");
		assertThat(exports).contains("uploadManaged", "SCOPE_CHANGED", "HttpStatus.GONE", "ocpx:report:deduction:export")
			.contains("LEFT JOIN scx_ocpx_hourly_report", "metric + \"Numerator\"", "metric + \"Status\"")
			.doesNotContain("System.getenv", "http://", "https://");
		assertThat(views).doesNotContain("objectKey", "storageObject", "secret", "ciphertext");
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("schema resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String source(String path) throws Exception {
		return java.nio.file.Files.readString(java.nio.file.Path.of("..", path));
	}

}
