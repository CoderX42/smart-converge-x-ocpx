package com.yzytech.scx.ocpx.runtime.persistence;

import com.yzytech.scx.ocpx.contract.OcpxPartnerContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Writes the single sanitized log used by both operations and reports. */
@Repository
public class OcpxFlowLogRepository {

	private final JdbcTemplate jdbcTemplate;

	public OcpxFlowLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void record(OcpxPartnerContext context, String flowType, String eventId, String sourceEventId,
			String eventCode, String payloadHash, String status, Integer partnerHttpStatus, String message,
			long durationMs, OffsetDateTime eventTime) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_flow_log
				(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,promotion_link_id,
				 media_id,monitor_task_id,result_status,partner_http_status,message,duration_ms,event_time,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
				""", id(), flowType, trim(eventId, 128), trim(sourceEventId, 128), trim(eventCode, 64), payloadHash,
				context.advertiserId(), context.promotionLinkId(), context.mediaId(), context.monitorTaskId(), status,
				partnerHttpStatus, trim(message, 500), durationMs, eventTime);
	}

	private String trim(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
