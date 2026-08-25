package com.yzytech.scx.ocpx.audit.store;

import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class WireAuditRepository {

	private final JdbcTemplate jdbcTemplate;

	@Transactional
	public void upsert(WireAuditRecord record) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_wire_audit
				(id,trace_id,flow_log_id,stage_code,direction,traffic_class,protocol,request_method,request_path,
				 wire_sent,result_status,partner_http_status,error_code,request_id,advertiser_id,promotion_link_id,
				 media_id,monitor_task_id,request_bytes,response_bytes,request_sha256,response_sha256,raw_state,
				 key_id,crypto_version,capture_source,captured_at,finished_at,raw_expires_at,metadata_expires_at)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				ON DUPLICATE KEY UPDATE
				 trace_id=VALUES(trace_id),flow_log_id=VALUES(flow_log_id),wire_sent=VALUES(wire_sent),
				 result_status=VALUES(result_status),partner_http_status=VALUES(partner_http_status),
				 error_code=VALUES(error_code),request_id=VALUES(request_id),advertiser_id=VALUES(advertiser_id),
				 promotion_link_id=VALUES(promotion_link_id),media_id=VALUES(media_id),
				 monitor_task_id=VALUES(monitor_task_id),request_bytes=VALUES(request_bytes),
				 response_bytes=VALUES(response_bytes),request_sha256=VALUES(request_sha256),
				 response_sha256=VALUES(response_sha256),raw_state=VALUES(raw_state),key_id=VALUES(key_id),
				 crypto_version=VALUES(crypto_version),capture_source=VALUES(capture_source),
				 finished_at=VALUES(finished_at),raw_expires_at=VALUES(raw_expires_at),
				 metadata_expires_at=VALUES(metadata_expires_at)
				""", record.getId(), record.getTraceId(), record.getFlowLogId(), record.getStageCode(),
				record.getDirection(), record.getTrafficClass(), record.getProtocol(), record.getRequestMethod(),
				record.getRequestPath(), record.isWireSent(), record.getResultStatus(), record.getPartnerHttpStatus(),
				record.getErrorCode(), record.getRequestId(), record.getAdvertiserId(), record.getPromotionLinkId(),
				record.getMediaId(), record.getMonitorTaskId(), record.getRequestBytes(), record.getResponseBytes(),
				record.getRequestSha256(), record.getResponseSha256(), record.getRawState(), record.getKeyId(),
				record.getCryptoVersion(), record.getCaptureSource(), record.getCapturedAt(), record.getFinishedAt(),
				record.getRawExpiresAt(), record.getMetadataExpiresAt());
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_wire_audit_payload
				(audit_id,key_id,crypto_version,payload_aad,payload_nonce,payload_ciphertext)
				VALUES (?,?,?,?,?,?)
				ON DUPLICATE KEY UPDATE key_id=VALUES(key_id),crypto_version=VALUES(crypto_version),
				 payload_aad=VALUES(payload_aad),payload_nonce=VALUES(payload_nonce),
				 payload_ciphertext=VALUES(payload_ciphertext)
				""", record.getId(), record.getKeyId(), record.getCryptoVersion(), record.getPayloadAad(),
				record.getPayloadNonce(), record.getPayloadCiphertext());
	}

}
