package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDeductionV113MigrationContractTest {

	private static final String MIGRATION = "db/migration/V113__separate_advertiser_billing_and_media_deduction.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationRejectsInvalidRatiosAndAddsAuditableDeductionFacts() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql)
			.contains("V113 blocked: stored media callback ratios must be between 0 and 1",
					"MODIFY COLUMN ratio DECIMAL(9,6)", "ck_ocpx_deduction_ratio", "deduction_decision VARCHAR(16)",
					"deduction_ratio_snapshot DECIMAL(9,6)", "deduction_decided_at DATETIME(3)",
					"idx_ocpx_event_fact_deduction_time", "callback_count BIGINT", "deduction_passed_count BIGINT",
					"deduction_deducted_count BIGINT", "ck_ocpx_event_fact_deduction_state",
					"deduction_passed_count+deduction_deducted_count<=callback_count")
			.doesNotContain("UPDATE scx_ocpx_event_fact SET deduction_decision", "scx_schema_migration");
	}

	@Test
	void currentSchemaCarriesTheSameRatioFactAndAggregateContract() throws Exception {
		String sql = resource(CURRENT_SCHEMA);

		assertThat(sql).contains("ratio DECIMAL(9,6) NOT NULL DEFAULT 1.000000", "ck_ocpx_deduction_ratio",
				"deduction_decision VARCHAR(16) NULL", "deduction_ratio_snapshot DECIMAL(9,6) NULL",
				"deduction_decided_at DATETIME(3) NULL", "idx_ocpx_event_fact_deduction_time",
				"callback_count BIGINT NOT NULL DEFAULT 0", "deduction_passed_count BIGINT NOT NULL DEFAULT 0",
				"deduction_deducted_count BIGINT NOT NULL DEFAULT 0");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
