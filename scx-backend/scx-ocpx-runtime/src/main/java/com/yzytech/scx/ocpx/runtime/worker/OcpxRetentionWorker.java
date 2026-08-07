package com.yzytech.scx.ocpx.runtime.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OcpxRetentionWorker {

	private static final int BATCH_SIZE = 10_000;

	private static final int MAX_BATCHES_PER_RUN = 10;

	private final JdbcTemplate jdbcTemplate;

	public OcpxRetentionWorker(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void purgeExpiredProtocolData() {
		purge("DELETE FROM scx_ocpx_callback_ref WHERE expires_at<NOW(3) LIMIT " + BATCH_SIZE);
		purge("DELETE FROM scx_ocpx_protocol_snapshot WHERE expires_at<NOW(3) LIMIT " + BATCH_SIZE);
		purge("DELETE FROM scx_ocpx_delivery_attempt WHERE create_time<DATE_SUB(NOW(3),INTERVAL 30 DAY) LIMIT "
				+ BATCH_SIZE);
		purge("DELETE FROM scx_ocpx_ingress_audit WHERE create_time<DATE_SUB(NOW(3),INTERVAL 30 DAY) LIMIT "
				+ BATCH_SIZE);
	}

	private void purge(String sql) {
		for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
			if (jdbcTemplate.update(sql) < BATCH_SIZE) {
				return;
			}
		}
	}

}
