package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowLogPreviewRetentionV129MigrationContractTest {

	@Test
	void migrationAddsIndexedPresenceFlagForSparsePreviewCleanup() throws Exception {
		String migration = Files
			.readString(Path.of("src/main/resources/db/migration/V129__optimize_flow_log_preview_retention.sql"));
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));

		assertThat(migration)
			.contains("response_preview_present", "idx_ocpx_flow_log_preview_retention", "VIRTUAL", "ALGORITHM=INPLACE",
					"LOCK=NONE")
			.doesNotContain("scx_schema_migration");
		assertThat(schema)
			.contains("response_preview_present", "KEY idx_ocpx_flow_log_preview_retention");
	}

}
