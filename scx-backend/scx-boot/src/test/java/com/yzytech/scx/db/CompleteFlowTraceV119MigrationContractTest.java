package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompleteFlowTraceV119MigrationContractTest {

	@Test
	void migrationAddsTraceDetailRetentionIndexesAndAdminOnlyPermission() throws Exception {
		String sql = Files.readString(Path.of("src/main/resources/db/migration/V119__add_complete_flow_trace.sql"));
		assertThat(sql)
			.contains("trace_id", "stage_code", "scx_ocpx_flow_log_detail", "idx_ocpx_flow_log_detail_expiry",
					"ON DELETE CASCADE", "ocpx:flow-log:detail", "ROLE_ADMIN")
			.doesNotContain("scx_schema_migration");
		assertThat(sql).contains("DELETE rm FROM sys_role_menu");
	}

}
