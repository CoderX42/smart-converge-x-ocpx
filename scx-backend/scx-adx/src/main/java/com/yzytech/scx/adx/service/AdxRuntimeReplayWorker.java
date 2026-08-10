package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.config.AdxRuntimeCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Database-recoverable S10 replay worker; disabled by literal configuration. */
@Slf4j
@Component
public class AdxRuntimeReplayWorker {

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final AdxRuntimeReplayProcessor processor;

	private final AdxRuntimeCenterProperties properties;

	private final String workerId = "s10-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

	public AdxRuntimeReplayWorker(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
			AdxRuntimeReplayProcessor processor, AdxRuntimeCenterProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.processor = processor;
		this.properties = properties;
	}

	@Scheduled(fixedDelay = 5000)
	public void scheduledDrain() {
		if (properties.isReplayWorkerEnabled()) {
			drainOnce();
		}
	}

	/** Returns zero without touching command rows when disabled or unconfigured. */
	public int drainOnce() {
		if (!properties.isReplayWorkerEnabled() || !processor.available()) {
			return 0;
		}
		List<Claim> claims = transactionTemplate.execute(status -> claim(properties.getMaxReplayBatch()));
		if (claims == null || claims.isEmpty()) {
			return 0;
		}
		for (Claim claim : claims) {
			AdxRuntimeReplayProcessor.Result result;
			try {
				result = processor.process(new AdxRuntimeReplayProcessor.Context(claim.commandId(), claim.failureId(),
						claim.routeVersionId(), claim.routeVersion(), claim.eventSnapshotSchemaVersion(),
						"replay:" + claim.failureId()));
			}
			catch (RuntimeException error) {
				log.warn("S10 replay processor failed: command={}, code=PROCESSOR_EXCEPTION", claim.commandId());
				result = AdxRuntimeReplayProcessor.Result.retry("RETRYABLE", "PROCESSOR_EXCEPTION");
			}
			AdxRuntimeReplayProcessor.Result finalResult = result;
			transactionTemplate.executeWithoutResult(status -> complete(claim, finalResult));
		}
		return claims.size();
	}

	private List<Claim> claim(int limit) {
		List<Claim> rows = jdbcTemplate.query("""
				SELECT r.id,r.source_failure_id,r.source_route_version_id,r.source_route_version,
				 f.event_snapshot_schema_version,r.attempt_count,r.max_attempts,r.tenant_id,r.advertiser_id,
				 r.product_id,r.app_link_id,r.task_id,r.channel_id,r.media_account_id,r.operator_user_id,
				 f.route_id,f.event_time,f.received_time
				FROM scx_adx_runtime_replay_request r
				JOIN scx_adx_runtime_failure f ON f.id=r.source_failure_id
				JOIN scx_adx_task t ON t.id=r.task_id AND t.deleted=0 AND t.status NOT IN ('PAUSED','ARCHIVED')
				JOIN scx_adx_route_runtime_state s ON s.route_version_id=r.source_route_version_id
				 AND s.acceptance_status<>'DENIED' AND s.deny_at IS NULL
				WHERE (r.status IN ('QUEUED','RETRY_WAIT') OR (r.status='CLAIMED' AND r.lease_until<NOW(3)))
				 AND r.next_attempt_at<=NOW(3) AND r.attempt_count<r.max_attempts
				 AND NOT EXISTS (SELECT 1 FROM scx_adx_runtime_replay_request active
				   WHERE active.tenant_id=r.tenant_id AND active.status='CLAIMED' AND active.lease_until>=NOW(3)
				     AND active.id<>r.id)
				ORDER BY r.next_attempt_at,r.id LIMIT ? FOR UPDATE SKIP LOCKED
				""",
				(rs, rowNum) -> new Claim(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
						(rs.getObject(5) == null ? 0 : rs.getInt(5)), rs.getInt(6), rs.getInt(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
						rs.getString(14), rs.getString(15), rs.getString(16), rs.getTimestamp(17).toLocalDateTime(),
						rs.getTimestamp(18).toLocalDateTime()),
				Math.min(limit, properties.getMaxReplayBatch()));
		for (Claim row : rows) {
			jdbcTemplate.update("""
					UPDATE scx_adx_runtime_replay_request
					SET status='CLAIMED',lease_owner=?,lease_until=DATE_ADD(NOW(3),INTERVAL ? SECOND),update_time=NOW(3)
					WHERE id=? AND (status IN ('QUEUED','RETRY_WAIT') OR (status='CLAIMED' AND lease_until<NOW(3)))
					""", workerId, properties.getLeaseSeconds(), row.commandId());
		}
		return rows;
	}

	private void complete(Claim claim, AdxRuntimeReplayProcessor.Result result) {
		Integer current = jdbcTemplate.queryForObject("""
				SELECT attempt_count FROM scx_adx_runtime_replay_request
				WHERE id=? AND status='CLAIMED' AND lease_owner=? FOR UPDATE
				""", Integer.class, claim.commandId(), workerId);
		if (current == null) {
			return;
		}
		int attemptNo = current + 1;
		boolean terminal = !result.success() && (!result.retryable() || attemptNo >= claim.maxAttempts());
		String attemptStatus = result.success() ? "SUCCEEDED" : terminal ? "TERMINAL" : "RETRY_WAIT";
		String attemptId = id();
		String summary = hash(String.join("|", claim.commandId(), Integer.toString(attemptNo), attemptStatus,
				String.valueOf(result.errorClass()), String.valueOf(result.errorCode())));
		LocalDateTime nextRetry = terminal || result.success() ? null
				: LocalDateTime.now().plusSeconds(backoffSeconds(attemptNo));
		jdbcTemplate.update("""
				INSERT INTO scx_adx_runtime_replay_attempt
				(id,replay_request_id,attempt_no,worker_id_hash,source_route_version_id,
				 source_event_snapshot_schema_version,status,error_class,error_code,result_summary_hash,
				 started_at,finished_at,next_retry_at,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3),?,NOW(3))
				""", attemptId, claim.commandId(), attemptNo, hash(workerId), claim.routeVersionId(),
				Math.max(1, claim.eventSnapshotSchemaVersion()), attemptStatus, result.errorClass(), result.errorCode(),
				summary, nextRetry);
		if (result.success()) {
			String resultFactId = appendSuccessFact(claim);
			String contributionId = appendContribution(claim, result.forwardedConversionCount());
			jdbcTemplate.update("""
					UPDATE scx_adx_runtime_replay_request SET status='SUCCEEDED',attempt_count=?,lease_owner=NULL,
					 lease_until=NULL,result_fact_id=?,result_contribution_id=?,last_error_class=NULL,
					 last_error_code=NULL,completed_at=NOW(3),update_time=NOW(3)
					WHERE id=? AND status='CLAIMED' AND lease_owner=?
					""", attemptNo, resultFactId, contributionId, claim.commandId(), workerId);
		}
		else {
			jdbcTemplate.update(
					"""
							UPDATE scx_adx_runtime_replay_request SET status=?,attempt_count=?,next_attempt_at=COALESCE(?,next_attempt_at),
							 lease_owner=NULL,lease_until=NULL,last_error_class=?,last_error_code=?,
							 completed_at=IF(?='TERMINAL',NOW(3),NULL),update_time=NOW(3)
							WHERE id=? AND status='CLAIMED' AND lease_owner=?
							""",
					terminal ? "TERMINAL" : "RETRY_WAIT", attemptNo, nextRetry, safeCode(result.errorClass()),
					safeCode(result.errorCode()), attemptStatus, claim.commandId(), workerId);
		}
	}

	private String appendSuccessFact(Claim claim) {
		String factId = id();
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_adx_runtime_fact
				(id,source_type,source_id,fact_type,direction,acceptance_status,processing_status,tenant_id,
				 advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
				 route_id,route_version_id,route_version,event_snapshot_schema_version,event_time,received_time,
				 idempotency_key_hash,summary_code,dimension_status,quality_status,create_time)
				VALUES (?,'REPLAY',?,'REPLAY_RESULT','INTERNAL','ACCEPTED','SUCCEEDED',?,?,?,?,?,?,?,?,?,?,?,
				 ?,?,?,?,'REPLAY_SUCCEEDED','FROZEN','COMPLETE',NOW(3))
				""", factId, claim.commandId(), claim.tenantId(), claim.advertiserId(), claim.productId(),
				claim.appLinkId(), claim.taskId(), claim.channelId(), claim.mediaAccountId(), claim.operatorUserId(),
				claim.routeId(), claim.routeVersionId(), claim.routeVersion(), claim.eventSnapshotSchemaVersion(),
				claim.eventTime(), LocalDateTime.now(), hash("runtime-fact|" + claim.commandId()));
		return jdbcTemplate.queryForObject("SELECT id FROM scx_adx_runtime_fact WHERE idempotency_key_hash=?",
				String.class, hash("runtime-fact|" + claim.commandId()));
	}

	private String appendContribution(Claim claim, long forwardedConversionCount) {
		String contributionId = id();
		LocalDateTime eventHour = claim.eventTime().truncatedTo(ChronoUnit.HOURS);
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_adx_hourly_fact_contribution
				(id,source_event_id,contribution_kind,tenant_id,advertiser_id,product_id,app_link_id,task_id,
				 channel_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
				 event_time,received_time,stat_hour,timezone,forwarded_conversion_count)
				VALUES (?,?, 'DELIVERY_SUCCESS',?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Asia/Shanghai',?)
				""", contributionId, "replay:" + claim.failureId(), claim.tenantId(), claim.advertiserId(),
				claim.productId(), claim.appLinkId(), claim.taskId(), claim.channelId(), claim.mediaAccountId(),
				claim.operatorUserId(), claim.routeId(), claim.routeVersionId(), claim.routeVersion(),
				claim.eventTime(), LocalDateTime.now(), eventHour, forwardedConversionCount);
		return jdbcTemplate.queryForObject("""
				SELECT id FROM scx_adx_hourly_fact_contribution
				WHERE source_event_id=? AND contribution_kind='DELIVERY_SUCCESS'
				""", String.class, "replay:" + claim.failureId());
	}

	private long backoffSeconds(int attemptNo) {
		return Math.min(3600, 30L * (1L << Math.min(attemptNo - 1, 6)));
	}

	private String safeCode(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
		return normalized.substring(0, Math.min(64, normalized.length()));
	}

	private String hash(String value) {
		try {
			return java.util.HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("Cannot calculate worker fingerprint", error);
		}
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record Claim(String commandId, String failureId, String routeVersionId, long routeVersion,
			int eventSnapshotSchemaVersion, int attemptCount, int maxAttempts, String tenantId, String advertiserId,
			String productId, String appLinkId, String taskId, String channelId, String mediaAccountId,
			String operatorUserId, String routeId, LocalDateTime eventTime, LocalDateTime receivedTime) {
	}

}
