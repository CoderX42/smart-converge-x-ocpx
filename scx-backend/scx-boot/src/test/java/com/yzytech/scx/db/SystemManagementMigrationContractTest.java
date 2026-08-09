package com.yzytech.scx.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SystemManagementMigrationContractTest {

	private static final String MIGRATION = "db/schema/scx_current_schema.sql";

	@Test
	void packagesTheOriginalSystemManagementTreeAndRbacRecovery() throws IOException {
		String sql;
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertThat(sql)
			.contains("('menu_ocpx_system','系统管理'")
			.contains("'menu_system_user','用户管理'")
			.contains("'menu_role','角色管理'")
			.contains("'menu_menu','菜单管理'")
			.contains("'menu_dept','部门管理'")
			.contains("'menu_dict','字典管理'")
			.contains("'menu_storage','存储配置'")
			.contains("'menu_log','操作日志'")
			.contains("'menu_login_log','登录日志'")
			.contains("permission LIKE 'system:sysuser:%'")
			.contains("permission LIKE 'system:sysrole:%'")
			.contains("permission LIKE 'system:sysmenu:%'")
			.contains("permission LIKE 'system:sysdept:%'")
			.contains("permission LIKE 'system:sysdict:%'")
			.contains("permission LIKE 'system:storageconfig:%'")
			.contains("permission LIKE 'system:syslog:%'")
			.contains("permission LIKE 'system:sysloginlog:%'")
			.contains("WHERE id IN ('menu_system','menu_user')")
			.contains("r.role_code = 'ROLE_ADMIN'");
	}

}
