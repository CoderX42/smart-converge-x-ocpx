package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FullWireAuditV121MigrationContractTest {

	@Test
	void migrationAddsEncryptedRawPayloadRetentionIndexesAndAdminOnlyPermissions() throws Exception {
		String sql = Files.readString(Path.of("src/main/resources/db/migration/V121__add_full_wire_audit.sql"));
		assertThat(sql)
			.contains("scx_ocpx_wire_audit", "scx_ocpx_wire_audit_payload", "payload_ciphertext LONGBLOB",
					"raw_expires_at", "metadata_expires_at", "ocpx:flow-log:raw", "ocpx:flow-log:raw:export",
					"ROLE_ADMIN", "DELETE rm FROM sys_role_menu")
			.doesNotContain("scx_schema_migration", "FOREIGN KEY");
	}

}
