package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class RtaAsyncSchemaContractTest {

	@Test
	void migrationAndCurrentSchemaAgreeWithoutLegacyDataConversion() throws Exception {
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));
		String migration = Files.readString(Path.of("src/main/resources/db/migration/V131__rebuild_async_rta.sql"));
		for (String table : List.of("upstream", "media_access", "binding", "log", "hour_stat")) {
			var pattern = Pattern.compile("CREATE TABLE IF NOT EXISTS scx_ocpx_rta_" + table + " \\(.*?;",
					Pattern.DOTALL);
			var current = pattern.matcher(schema);
			var forward = pattern.matcher(migration);
			assertThat(current.find()).isTrue();
			assertThat(forward.find()).isTrue();
			if (!List.of("upstream", "binding").contains(table))
				assertThat(forward.group()).isEqualTo(current.group());
		}
		String extension = Files.readString(
				Path.of("src/main/resources/db/migration/V132__extend_rta_protocols_and_embed_configuration.sql"));
		assertThat(extension).contains("ADD COLUMN protocol_config", "config_schema_version", "MODIFY product_id",
				"menu_ocpx_advertiser", "menu_ocpx_monitor_task");
		assertThat(extension).doesNotContain("DROP TABLE", "scx_schema_migration");
		assertThat(schema).contains("protocol_config JSON", "config_schema_version INT",
				"parent_id='menu_ocpx_advertiser' WHERE id='menu_ocpx_rta_upstreams'");
		assertThat(schema).doesNotContain("CHECK(protocol='GENERIC_ASYNC')");
		for (String table : List.of("profile", "task_binding", "decision_fact", "hourly_metric"))
			assertThat(schema).doesNotContain("CREATE TABLE IF NOT EXISTS scx_ocpx_rta_" + table + " (");
		for (String child : List.of("upstreams", "media", "bindings", "records", "logs", "reports"))
			assertThat(migration).contains("ocpx:rta:" + child + ":view", "ocpx/rta/" + child + "/index");
		assertThat(migration).doesNotContain("DROP TABLE", "scx_schema_migration", "INSERT INTO scx_ocpx_rta_");
		assertThat(migration).contains("SET rta_enabled=0", "DROP FOREIGN KEY", "DEFAULT 0");
	}

}
