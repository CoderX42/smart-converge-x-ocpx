package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxAdapterCapabilityMigrationContractTest {

	private static final String MIGRATION = "db/schema/scx_current_schema.sql";

	@Test
	void publishesProtocolDrivenCapabilitiesForAllBuiltInAdapters() throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(sql).contains("'COCO'", "'TEMPLATE_GET'", "'STANDARD'", "'TEMPLATE_GET_DOWNSTREAM'",
					"'supportedMacros'", "'requiredUrlMacros'", "'requiredAnyUrlMacros'");
			assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM scx_ocpx_adapter");
		}
	}

}
