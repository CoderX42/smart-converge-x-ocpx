package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class OppoMediaSchemaContractTest {

	@Test
	void extendsExistingMediaAndTaskBindingWithoutNewAccountEntity() throws Exception {
		String sql = Files.readString(Path.of("src/main/resources/db/migration/V133__add_oppo_media_protocol.sql"));
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));
		assertThat(sql).contains("ALTER TABLE scx_ocpx_rta_media_access", "ALTER TABLE scx_ocpx_rta_binding");
		for (String field : new String[] { "protocol VARCHAR(32) NOT NULL DEFAULT 'GENERIC_ASYNC'",
				"protocol_config JSON NULL", "strategy_id INT NOT NULL AUTO_INCREMENT UNIQUE",
				"oppo_pkg VARCHAR(128) NOT NULL DEFAULT ''" }) {
			assertThat(sql).contains(field);
			assertThat(schema).contains(field);
		}
		assertThat(sql).doesNotContain("DROP TABLE", "CREATE TABLE", "scx_schema_migration");
	}

}
