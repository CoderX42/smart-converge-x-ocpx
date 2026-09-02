package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackRecordSearchPerformanceV126MigrationContractTest {

	@Test
	void migrationAddsSelectiveOrderedCallbackRecordIndexWithoutMigrationLedgerDependency() throws Exception {
		String migration = Files
			.readString(Path.of("src/main/resources/db/migration/V126__optimize_callback_record_search.sql"));
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));

		assertThat(migration)
			.contains("ALTER TABLE scx_ocpx_event_fact",
					"idx_ocpx_event_fact_callback_page (flow_type,received_at,id)", "ALGORITHM=INPLACE", "LOCK=NONE")
			.doesNotContain("scx_schema_migration");
		assertThat(schema).contains("KEY idx_ocpx_event_fact_callback_page (flow_type,received_at,id)");
	}

}
