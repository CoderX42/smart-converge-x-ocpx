package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowLogSearchPerformanceV123MigrationContractTest {

	@Test
	void migrationAddsEventTimePaginationIndexWithoutMigrationLedgerDependency() throws Exception {
		String migration = Files
			.readString(Path.of("src/main/resources/db/migration/V123__optimize_flow_log_search.sql"));
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));

		assertThat(migration)
			.contains("ALTER TABLE scx_ocpx_flow_log", "idx_ocpx_flow_log_page (event_time,id)", "ALGORITHM=INPLACE",
					"LOCK=NONE")
			.doesNotContain("scx_schema_migration");
		assertThat(schema).contains("KEY idx_ocpx_flow_log_page (event_time,id)");
	}

}
