package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RtaOverviewMenuV134MigrationContractTest {

	@Test
	void overviewMenuIsSeededInMigrationAndCurrentSchema() throws Exception {
		String migration = Files.readString(Path.of("src/main/resources/db/migration/V134__add_rta_overview_menu.sql"));
		String schema = Files.readString(Path.of("src/main/resources/db/schema/scx_current_schema.sql"));
		String verify = Files.readString(Path.of("../db/smart-converge-x/verify-database.sh"));
		String row = "('menu_ocpx_rta_overview','接入总览','/ocpx/rta/overview','menu_ocpx_rta','lucide:activity',"
				+ "'ocpx/rta/overview/index','ocpx:rta:bindings:view',0,'0'";
		for (String sql : new String[] { migration, schema }) {
			assertThat(sql).contains(row, "WHERE rm.menu_id='menu_ocpx_rta_bindings'", "r.role_code='ROLE_ADMIN'");
		}
		assertThat(migration).doesNotContain("DROP ", "DELETE ", "scx_schema_migration");
		assertThat(verify).contains("menu_ocpx_rta_overview", "\"$rta_menu_count\" -eq 4");
	}

}
