package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Applies instance acknowledgement evidence to the mutable distribution/runtime
 * dimension. This service never changes an immutable RouteVersion snapshot.
 */
@Service
@RequiredArgsConstructor
public class OcpxRouteDistributionService {

	private final JdbcTemplate jdbcTemplate;

	private final OcpxTrackingProperties properties;

	@Transactional
	public AckResult acknowledge(String distributionId, String instanceId, long appliedVersion, String requestedStatus,
			String requestedErrorCode) {
		Distribution row = requireDistribution(distributionId);
		String status = "APPLIED".equals(requestedStatus) && appliedVersion == row.routeVersion() ? "APPLIED"
				: "FAILED";
		String errorCode = "APPLIED".equals(status) ? null
				: appliedVersion != row.routeVersion() ? "VERSION_MISMATCH" : sanitizeCode(requestedErrorCode);
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_route_instance_ack
				(id,distribution_id,route_version_id,instance_id,applied_version,status,error_code,acked_at,create_time)
				VALUES (?,?,?,?,?,?,?,NOW(3),NOW(3))
				ON DUPLICATE KEY UPDATE applied_version=VALUES(applied_version),status=VALUES(status),
				 error_code=VALUES(error_code),acked_at=NOW(3)
				""", id(), distributionId, row.routeVersionId(), requireInstanceId(instanceId), appliedVersion, status,
				errorCode);
		Counts counts = jdbcTemplate.queryForObject(
				"""
						SELECT SUM(status='APPLIED'),SUM(status='FAILED') FROM scx_ocpx_route_instance_ack WHERE distribution_id=?
						""",
				(rs, rowNum) -> new Counts(rs.getInt(1), rs.getInt(2)), distributionId);
		String distributionStatus = resolveDistributionStatus(row.minimumRequiredAcks(), counts.applied(),
				counts.failed());
		jdbcTemplate.update("""
				UPDATE scx_ocpx_route_distribution SET status=?,lease_owner=NULL,lease_until=NULL,
				 last_error_code=?,update_time=NOW(3) WHERE id=?
				""", distributionStatus, counts.failed() > 0 ? "INSTANCE_APPLY_FAILED" : null, distributionId);
		if (counts.applied() >= row.minimumRequiredAcks()) {
			activate(row);
		}
		else {
			deactivateBelowThreshold(row);
		}
		return new AckResult(distributionId, row.routeVersionId(), row.routeVersion(), instanceId, status, errorCode,
				distributionStatus, counts.applied(), counts.failed(), LocalDateTime.now());
	}

	private void activate(Distribution row) {
		if (properties.getAddressOverlapSeconds() > 0) {
			jdbcTemplate.update(
					"""
								UPDATE scx_ocpx_route_runtime_state s JOIN scx_ocpx_route_version v ON v.id=s.route_version_id
								SET s.acceptance_status='OVERLAP',s.accept_until=DATE_ADD(NOW(3),INTERVAL ? SECOND),
								 s.state_revision=s.state_revision+1,s.reason_code='NEW_VERSION_ACTIVE',s.update_by='distribution-ack'
								WHERE v.monitor_id=? AND v.route_version<? AND s.acceptance_status='ACTIVE'
							""",
					properties.getAddressOverlapSeconds(), row.monitorId(), row.routeVersion());
		}
		else {
			jdbcTemplate.update("""
					UPDATE scx_ocpx_route_runtime_state s JOIN scx_ocpx_route_version v ON v.id=s.route_version_id
					SET s.acceptance_status='SUPERSEDED',s.accept_until=NOW(3),s.state_revision=s.state_revision+1,
					 s.reason_code='NEW_VERSION_ACTIVE',s.update_by='distribution-ack'
					WHERE v.monitor_id=? AND v.route_version<? AND s.acceptance_status IN ('ACTIVE','OVERLAP')
					""", row.monitorId(), row.routeVersion());
		}
		int activated = jdbcTemplate.update(
				"""
								UPDATE scx_ocpx_route_runtime_state SET acceptance_status='ACTIVE',accept_from=COALESCE(accept_from,NOW(3)),
								 accept_until=NULL,deny_at=NULL,state_revision=state_revision+1,reason_code='INSTANCE_ACK_THRESHOLD',
								 update_by='distribution-ack' WHERE route_version_id=? AND acceptance_status='PENDING_DISTRIBUTION'
						""",
				row.routeVersionId());
		if (activated > 0) {
			enqueueRuntimeSignal("ROUTE_ACTIVE", row);
		}
		int expectedRoutes = jdbcTemplate.queryForObject(
				"SELECT IF(exposure_enabled=1,2,1) FROM scx_ocpx_task WHERE id=?", Integer.class, row.taskId());
		int activeLatest = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_route_version v
				JOIN scx_ocpx_route_runtime_state s ON s.route_version_id=v.id AND s.acceptance_status='ACTIVE'
				JOIN scx_ocpx_route_distribution d ON d.route_version_id=v.id AND d.status IN ('ACTIVE','DEGRADED')
				WHERE v.task_id=? AND v.route_version=(SELECT MAX(v2.route_version) FROM scx_ocpx_route_version v2
				 WHERE v2.monitor_id=v.monitor_id)
				""", Integer.class, row.taskId());
		if (activeLatest >= expectedRoutes) {
			jdbcTemplate.update("""
					UPDATE scx_ocpx_task SET status='ACTIVE',update_by='distribution-ack',update_time=NOW(3)
					WHERE id=? AND status='READY' AND republish_required=0
					""", row.taskId());
		}
	}

	private void deactivateBelowThreshold(Distribution row) {
		int deactivated = jdbcTemplate.update("""
				UPDATE scx_ocpx_route_runtime_state
				SET acceptance_status='PENDING_DISTRIBUTION',accept_from=NULL,accept_until=NULL,
				 state_revision=state_revision+1,reason_code='INSTANCE_ACK_BELOW_THRESHOLD',update_by='distribution-ack'
				WHERE route_version_id=? AND acceptance_status='ACTIVE' AND deny_at IS NULL
				""", row.routeVersionId());
		if (deactivated > 0) {
			jdbcTemplate.update("""
					UPDATE scx_ocpx_task SET status='READY',update_by='distribution-ack',update_time=NOW(3)
					WHERE id=? AND status='ACTIVE'
					""", row.taskId());
			enqueueRuntimeSignal("ROUTE_DEGRADED", row);
		}
	}

	private void enqueueRuntimeSignal(String eventType, Distribution row) {
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_config_outbox
				(id,aggregate_type,aggregate_id,config_version,event_type,payload_json,published,delivery_status,
				 attempt_count,available_at,create_time,update_time)
				VALUES (?,?,?,?,?,JSON_OBJECT('referenceId',?),0,'PENDING',0,NOW(3),NOW(3),NOW(3))
				""", id(), eventType, row.routeVersionId(), row.routeVersion(), eventType, row.id());
	}

	private Distribution requireDistribution(String distributionId) {
		List<Distribution> rows = jdbcTemplate.query("""
				SELECT d.id,d.route_version_id,d.monitor_id,d.route_version,d.minimum_required_acks,v.task_id
				FROM scx_ocpx_route_distribution d JOIN scx_ocpx_route_version v ON v.id=d.route_version_id
				WHERE d.id=? FOR UPDATE
				""", (rs, rowNum) -> new Distribution(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
				rs.getInt(5), rs.getString(6)), distributionId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("distribution does not exist");
		}
		return rows.get(0);
	}

	private String requireInstanceId(String instanceId) {
		if (instanceId == null || !instanceId.matches("^[A-Za-z0-9._-]{1,100}$")) {
			throw new IllegalArgumentException("invalid instance id");
		}
		return instanceId;
	}

	private String sanitizeCode(String value) {
		return value != null && value.matches("^[A-Z0-9_]{1,64}$") ? value : "INSTANCE_APPLY_FAILED";
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	static String resolveDistributionStatus(int minimumRequiredAcks, int applied, int failed) {
		if (minimumRequiredAcks < 1 || applied < 0 || failed < 0) {
			throw new IllegalArgumentException("invalid distribution counters");
		}
		if (applied >= minimumRequiredAcks) {
			return failed > 0 ? "DEGRADED" : "ACTIVE";
		}
		return failed > 0 ? "DEGRADED" : "DISTRIBUTING";
	}

	private record Distribution(String id, String routeVersionId, String monitorId, long routeVersion,
			int minimumRequiredAcks, String taskId) {
	}

	private record Counts(int applied, int failed) {
	}

	public record AckResult(String distributionId, String routeVersionId, long routeVersion, String instanceId,
			String status, String errorCode, String distributionStatus, int appliedCount, int failedCount,
			LocalDateTime ackedAt) {
	}

}
