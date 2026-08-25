package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FullWireAuditV122RemovalContractTest {

	@Test
	void forwardRemovalDropsPayloadBeforeMetadataAndRetiresPermissions() throws Exception {
		String sql = Files.readString(Path.of("src/main/resources/db/migration/V122__remove_full_wire_audit.sql"));

		assertThat(sql).contains("DROP TABLE IF EXISTS scx_ocpx_wire_audit_payload",
				"DROP TABLE IF EXISTS scx_ocpx_wire_audit", "perm_ocpx_flow_log_raw",
				"perm_ocpx_flow_log_raw_export", "ocpx:flow-log:raw", "ocpx:flow-log:raw:export");
		assertThat(sql.indexOf("DROP TABLE IF EXISTS scx_ocpx_wire_audit_payload"))
			.isLessThan(sql.indexOf("DROP TABLE IF EXISTS scx_ocpx_wire_audit;"));
		assertThat(sql).doesNotContain("scx_schema_migration");
	}

	@Test
	void currentSchemaDoesNotRecreateRetiredAuditObjects() throws Exception {
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));

		assertThat(schema).doesNotContain("scx_ocpx_wire_audit", "perm_ocpx_flow_log_raw",
				"ocpx:flow-log:raw");
	}

}
