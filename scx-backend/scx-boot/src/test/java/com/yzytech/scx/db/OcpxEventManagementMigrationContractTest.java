package com.yzytech.scx.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxEventManagementMigrationContractTest {

	private static final String MIGRATION = "db/migration/V80__add_channel_event_management_and_cpa_runtime.sql";

	@Test
	void addsRepeatSafeEventDictionariesSnapshotsQuotaAndAdminRbac() throws Exception {
		try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
			assertThat(input).as("migration resource %s", MIGRATION).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(sql)
				.contains("CREATE TABLE IF NOT EXISTS scx_adx_event_definition")
				.contains("CREATE TABLE IF NOT EXISTS scx_adx_channel_event_mapping")
				.contains("CREATE TABLE IF NOT EXISTS scx_adx_monitor_event_binding")
				.contains("CREATE TABLE IF NOT EXISTS scx_adx_route_event_snapshot")
				.contains("CREATE TABLE IF NOT EXISTS scx_ocpx_event_daily_quota")
				.contains("event_snapshot_version", "source_event_code", "conversion_event_code", "media_event_code",
						"cpa_charge_amount", "scx_ocpx_billing_entry ADD COLUMN channel_id")
				.contains("ACTIVATE", "ACQUISITION", "ALIPAY_ASSIST_SUCCESS_5S", "RETENTION_D7")
				.contains("menu_adx_event", "adx:event:read", "adx:event:write", "ROLE_ADMIN")
				.doesNotContain("DROP TABLE", "DELETE FROM scx_ocpx_event", "UPDATE scx_ocpx_billing_entry SET status='REFUNDED'");
		}
	}

}
