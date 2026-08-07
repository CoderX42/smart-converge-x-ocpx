package com.yzytech.scx.ocpx.runtime.worker;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OcpxRetentionWorkerTest {

	@Test
	void purgesOnlyExpiringProtocolAndThirtyDayAuditData() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);

		new OcpxRetentionWorker(jdbcTemplate).purgeExpiredProtocolData();

		verify(jdbcTemplate).update(contains("scx_ocpx_callback_ref"));
		verify(jdbcTemplate).update(contains("scx_ocpx_protocol_snapshot"));
		verify(jdbcTemplate).update(contains("scx_ocpx_delivery_attempt"));
		verify(jdbcTemplate).update(contains("scx_ocpx_ingress_audit"));
	}

}
