package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlowLogResponsePreviewV117MigrationContractTest {

	private static final String MIGRATION = "db/migration/V117__add_flow_log_response_preview.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationAddsOnlyBoundedResponsePreview() throws Exception {
		assertThat(resource(MIGRATION)).contains("ALTER TABLE scx_ocpx_flow_log")
			.contains("response_preview VARCHAR(4096) NULL")
			.doesNotContain("request_url", "raw_url", "ADD COLUMN sign", "ADD COLUMN secret", "scx_schema_migration");
	}

	@Test
	void currentSchemaCarriesBoundedResponsePreview() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("response_preview VARCHAR(4096) NULL")
			.doesNotContain("request_url", "raw_url");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
