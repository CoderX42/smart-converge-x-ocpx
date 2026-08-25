package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxFourEntityResetContractTest {

	private static final String CURRENT_SCHEMA = "db/schema/scx_current_schema.sql";

	private static final String RESET = "db/migration/V96__reset_ocpx_ad_business_to_four_entities.sql";

	private static final List<String> BUSINESS_TABLES = List.of("scx_ocpx_advertiser", "scx_ocpx_promotion_link",
			"scx_ocpx_creative", "scx_ocpx_media", "scx_ocpx_monitor_task", "scx_ocpx_deduction_rule");

	private static final List<String> RETIRED_TABLES = List.of("scx_ocpx_channel", "scx_ocpx_app_link",
			"scx_ocpx_monitor_link", "scx_ocpx_hourly_report", "scx_ocpx_adapter", "scx_ocpx_product",
			"scx_ocpx_media_account", "scx_ocpx_media_credential", "scx_ocpx_task");

	@Test
	void currentSchemaHasCreativeManagementAndLegacyCompatibleDataLayer() throws Exception {
		String schema = resource(CURRENT_SCHEMA);

		assertThat(occurrences(schema, "COMMENT='BUSINESS:")).isEqualTo(6);
		BUSINESS_TABLES.forEach(table -> assertThat(schema).contains("CREATE TABLE IF NOT EXISTS " + table));
		RETIRED_TABLES.forEach(table -> assertThat(schema).doesNotContain("CREATE TABLE IF NOT EXISTS " + table + " "));
		assertThat(schema).contains("FOREIGN KEY (advertiser_id,promotion_link_id)",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_creative",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_protocol_allowed_host",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_creative_promotion_link",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_billing_config_audit",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_account",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_event_fact", "CREATE TABLE IF NOT EXISTS scx_ocpx_billing_entry",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_metric", "CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log_detail",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback_day",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_log_callback",
				"CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat_temp", "CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat",
				"advertiser_id", "promotion_link_id", "media_id", "monitor_task_id",
				"flow_type IN ('FEEDBACK','CALLBACK')", "partner_action_type", "standard_action_type",
				"action_time BIGINT", "currency_amount DECIMAL(20,6)", "first_activation", "first_order")
			.doesNotContain("CREATE TABLE IF NOT EXISTS scx_ocpx_report_hourly")
			.doesNotContain("credential_ciphertext", "secret_ciphertext", "password_hash");
		assertThat(occurrences(schema, "CREATE TABLE IF NOT EXISTS scx_ocpx_")).isEqualTo(20);
	}

	@Test
	void resetIsExplicitRepeatSafeAndProtectsSystemManagement() throws Exception {
		String reset = resource(RESET);

		assertThat(reset)
			.contains("@SCX_CONFIRM_RESET_OCPX", "DATABASE()='smart_converge_x'",
					"four-entity reset is already applied", "@v96_sys_user_count", "@v96_sys_role_count",
					"@v96_sys_dept_count", "@v96_sys_dict_count", "@v96_sys_dict_value_count", "@v96_sys_log_count",
					"@v96_sys_login_log_count", "@v96_sys_storage_config_count", "@v96_sys_user_role_count",
					"@v96_system_menu_count", "@v96_system_role_menu_count", "id='menu_ocpx_system'",
					"source ../schema/scx_current_schema.sql")
			.doesNotContain("DROP TABLE IF EXISTS sys_", "DELETE FROM sys_dept", "DELETE FROM sys_dict",
					"DELETE FROM sys_menu WHERE id='menu_ocpx_system'");
		assertThat(reset).doesNotMatch("(?s).*DELETE\\s+FROM\\s+sys_(?:user|role)\\s*(?:;|WHERE).*");
		RETIRED_TABLES.forEach(table -> assertThat(reset).contains(table));
	}

	private int occurrences(String text, String needle) {
		return (text.length() - text.replace(needle, "").length()) / needle.length();
	}

	private String resource(String path) throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
