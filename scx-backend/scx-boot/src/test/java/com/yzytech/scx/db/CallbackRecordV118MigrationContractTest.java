package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackRecordV118MigrationContractTest {

	private static final String MIGRATION = "db/migration/V118__add_upstream_callback_record_menu.sql";

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	@Test
	void migrationAddsScopedReadOnlyCallbackRecordMenu() throws Exception {
		assertThat(resource(MIGRATION))
			.contains("'menu_ocpx_callback_record'", "'上游转化记录'", "'/ocpx/callback-record'",
					"'ocpx/callback-record/index'", "'ocpx:callback-record:view'", "'ROLE_ADMIN','ROLE_ADVERTISER'")
			.doesNotContain("scx_schema_migration", "INSERT INTO scx_ocpx_event_fact", "DELETE FROM");
	}

	@Test
	void currentSchemaCarriesCallbackRecordMenuAndAdvertiserDefaultGrant() throws Exception {
		assertThat(resource(CURRENT_SCHEMA)).contains("'menu_ocpx_callback_record'", "'ocpx:callback-record:view'",
				"'menu_ocpx_report','menu_ocpx_callback_record','menu_ocpx_flow_log'");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
