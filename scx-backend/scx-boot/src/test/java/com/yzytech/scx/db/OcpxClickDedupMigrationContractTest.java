package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxClickDedupMigrationContractTest {

	private static final String MIGRATION = "db/migration/V77__add_ocpx_click_deduplication.sql";

	@Test
	void addsForwardOnlyClickDeduplicationStateAndDecisionTables() throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(sql).contains("click_dedup_window_seconds", "scx_ocpx_click_dedup_state",
					"scx_ocpx_click_dedup_decision", "device_platform", "device_id_type", "device_key_version");
			assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM scx_ocpx_event");
		}
	}
}
