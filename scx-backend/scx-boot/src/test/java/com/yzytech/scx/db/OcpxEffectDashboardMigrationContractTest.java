package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxEffectDashboardMigrationContractTest {

	private static final String MIGRATION = "db/migration/V79__reconcile_ocpx_click_dedup_and_dashboard.sql";

	@Test
	void addsForwardOnlyDashboardFactsAndEvidenceBasedBackfill() throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		assertThat(sql).contains("eligible_click_count", "deduplicated_click_count", "skipped_click_count",
				"classified_click_count", "first_activate_count", "reactivation_count", "retention_3d_count",
				"retention_4d_count", "retention_5d_count", "retention_6d_count", "retention_7d_count",
				"scx_ocpx_click_dedup_decision", "information_schema.tables", "e.is_debug=0", "IF NOT EXISTS");
			assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM scx_ocpx_event");
		}
	}

}
