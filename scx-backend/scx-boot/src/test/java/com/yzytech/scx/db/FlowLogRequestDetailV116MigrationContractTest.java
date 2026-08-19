package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlowLogRequestDetailV116MigrationContractTest {

	private static final String MIGRATION = "db/migration/V116__add_flow_log_request_detail.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	private static final String[] DETAIL_COLUMNS = { "device_androidid_md5", "device_idfa_md5", "aaid", "device_caid2",
			"device_caid_list", "last_caid", "prev_caid", "device_imei_md5", "device_oaid_md5", "ipv6", "model",
			"osv" };

	@Test
	void migrationAddsNormalizedDetailToBothFeedbackTablesWithoutRawRequestOrSecrets() throws Exception {
		String sql = resource(MIGRATION);

		assertThat(sql).contains("ALTER TABLE scx_ocpx_log_feedback", "ALTER TABLE scx_ocpx_log_feedback_day")
			.contains(DETAIL_COLUMNS)
			.doesNotContain("raw_query", "raw_url", "ADD COLUMN sign", "ADD COLUMN secret", "scx_schema_migration");
	}

	@Test
	void currentSchemaCarriesNormalizedDetailWithoutRawRequestOrSecrets() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains(DETAIL_COLUMNS).doesNotContain("raw_query", "raw_url");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
