package com.yzytech.scx.ocpx.runtime.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.BillingPort;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.LogDataProjection;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.model.DeliveryContext;
import com.yzytech.scx.ocpx.runtime.model.DeliveryAttemptAudit;
import com.yzytech.scx.ocpx.runtime.model.DeliveryJob;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OcpxPersistenceRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(OcpxPersistenceRepository.class);

	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final ProtocolCrypto protocolCrypto;

	private final OcpxRuntimeProperties properties;

	private final BillingPort billingPort;

	private final String leaseOwner = UUID.randomUUID().toString();

	public OcpxPersistenceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			ProtocolCrypto protocolCrypto, OcpxRuntimeProperties properties, BillingPort billingPort) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.protocolCrypto = protocolCrypto;
		this.properties = properties;
		this.billingPort = billingPort;
	}

	@Transactional
	public void persistFeedbackBatch(List<FeedbackEnvelope> envelopes) {
		for (FeedbackEnvelope envelope : envelopes) {
			try (MDC.MDCCloseable ignored = openDebugMdc(envelope.debugExecutionId())) {
				if (StringUtils.hasText(envelope.debugExecutionId())) {
					LOGGER.info("Persisting correlated API debug feedback event={}", envelope.eventId());
				}
				persistFeedback(envelope);
			}
		}
	}

	private void persistFeedback(FeedbackEnvelope envelope) {
		try {
			RouteSnapshot route = objectMapper.readValue(protocolCrypto.decrypt(envelope.encryptedRoute()),
					RouteSnapshot.class);
			int inserted = jdbcTemplate.update("""
					INSERT IGNORE INTO scx_ocpx_event
					(id,event_id,parent_event_id,source_direction,event_type,channel_id,advertiser_id,app_link_id,
					 monitor_id,route_version,occurred_at,received_at,device_key_hash,payload_hash,status,callback_ref,
					 debug_execution_id,is_debug)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", id(), envelope.eventId(), null, "DOWNSTREAM_FEEDBACK", envelope.eventType().name(),
					route.channelId(), route.advertiserId(), route.appLinkId(), route.routeId(), route.routeVersion(),
					Timestamp.from(envelope.occurredAt().toInstant()),
					Timestamp.from(envelope.receivedAt().toInstant()), envelope.deviceKeyHash(), envelope.payloadHash(),
					"PERSISTED", envelope.callbackRef(), envelope.debugExecutionId(),
					envelope.debugExecutionId() == null ? 0 : 1);
			if (inserted == 0) {
				completeDebug(envelope.debugExecutionId(), "SUCCEEDED", "DUPLICATE", "幂等事件已存在，未重复写入");
				return;
			}
			BillingPort.BillingResult billing = BillingPort.BillingResult.disabled();
			if (envelope.eventType() == OcpxEventType.CLICK && !"OFF".equals(route.billingMode())) {
				billing = envelope.debugExecutionId() == null
						? billingPort.charge(envelope.eventId(), route.advertiserId(), route.unitPrice(), "CNY",
								route.billingMode())
						: billingPort.charge(envelope.eventId(), route.advertiserId(), route.unitPrice(), "CNY",
								route.billingMode(), envelope.debugExecutionId());
				if (!billing.permitsDelivery()) {
					jdbcTemplate.update("""
							UPDATE scx_ocpx_event SET status='BILLING_REJECTED'
							WHERE event_id=? AND source_direction='DOWNSTREAM_FEEDBACK'
							""", envelope.eventId());
				}
			}
			if (envelope.forwardUpstream() && billing.permitsDelivery()) {
				jdbcTemplate.update("""
						INSERT INTO scx_ocpx_protocol_snapshot
						(feedback_event_id,feedback_ciphertext,route_ciphertext,expires_at,create_time)
						VALUES (?,?,?,?,NOW(3))
						""", envelope.eventId(), envelope.encryptedFeedback(), envelope.encryptedRoute(),
						Timestamp.from(envelope.receivedAt().plus(properties.getSnapshotTtl()).toInstant()));
				jdbcTemplate.update("""
						INSERT INTO scx_ocpx_callback_ref
						(id,feedback_event_id,route_version,debug_execution_id,expires_at,create_time)
						VALUES (?,?,?,?,?,NOW(3))
						""", envelope.callbackRef(), envelope.eventId(), route.routeVersion(),
						envelope.debugExecutionId(),
						Timestamp.from(envelope.receivedAt().plus(properties.getCallbackTokenTtl()).toInstant()));
				jdbcTemplate.update(
						"""
								INSERT IGNORE INTO scx_ocpx_delivery_job
								(id,event_id,source_event_id,direction,adapter_code,status,attempt_count,next_retry_at,callback_ref,debug_execution_id,
								 create_time,update_time)
								VALUES (?,?,?,'FEEDBACK_UPSTREAM',?,'PENDING',0,NOW(3),?,?,NOW(3),NOW(3))
								""",
						id(), envelope.eventId(), envelope.eventId(), route.upstreamAdapterCode(),
						envelope.callbackRef(), envelope.debugExecutionId());
			}
			if (envelope.debugExecutionId() == null) {
				upsertFeedbackMetric(envelope, route, billing.posted() ? route.unitPrice() : BigDecimal.ZERO);
			}
			String status = envelope.forwardUpstream() && billing.permitsDelivery() ? "WAITING_ASYNC"
					: billing.permitsDelivery() ? "SUCCEEDED" : "PARTIAL";
			completeDebug(envelope.debugExecutionId(), status, "PERSISTED", "反馈事实与账务处理已完成");
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot persist feedback event", e);
		}
	}

	public CallbackContext requireCallbackContext(String callbackRef) {
		try {
			return jdbcTemplate.queryForObject("""
					SELECT r.id,r.feedback_event_id,s.feedback_ciphertext,s.route_ciphertext,r.debug_execution_id
					FROM scx_ocpx_callback_ref r
					JOIN scx_ocpx_protocol_snapshot s ON s.feedback_event_id=r.feedback_event_id
					WHERE r.id=? AND r.expires_at>NOW(3)
					""", (rs, rowNum) -> {
				FeedbackReqV1 feedback = decrypt(rs.getString(3), FeedbackReqV1.class);
				RouteSnapshot route = decrypt(rs.getString(4), RouteSnapshot.class);
				return new CallbackContext(rs.getString(1), rs.getString(2), route, feedback, rs.getString(5));
			}, callbackRef);
		}
		catch (EmptyResultDataAccessException e) {
			throw new IllegalArgumentException("callback reference not found or expired");
		}
	}

	public Optional<Boolean> findDeductionDecision(String callbackEventId) {
		try {
			Boolean forwarded = jdbcTemplate.queryForObject(
					"SELECT forwarded FROM scx_ocpx_deduction_decision WHERE callback_event_id=?", Boolean.class,
					callbackEventId);
			return Optional.ofNullable(forwarded);
		}
		catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	@Transactional
	public boolean persistCallback(NormalizedCallbackEvent callback, CallbackContext context, boolean forwarded,
			String decisionReason) {
		RouteSnapshot route = context.route();
		int inserted = jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_event
				(id,event_id,parent_event_id,source_direction,event_type,channel_id,advertiser_id,app_link_id,
				 monitor_id,route_version,occurred_at,received_at,payload_hash,status,callback_ref,amount,amount_raw,
				 debug_execution_id,is_debug)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""", id(), callback.eventId(), context.feedbackEventId(), "UPSTREAM_CALLBACK",
				callback.eventType().name(), route.channelId(), route.advertiserId(), route.appLinkId(),
				route.routeId(), route.routeVersion(), Timestamp.from(callback.actionTime().toInstant()),
				Timestamp.from(OffsetDateTime.now().toInstant()), callback.eventId(), "PERSISTED",
				context.callbackRef(), callback.amount(), callback.amountRaw(), context.debugExecutionId(),
				context.debugExecutionId() == null ? 0 : 1);
		if (inserted == 0) {
			return false;
		}
		jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_deduction_decision
						(id,callback_event_id,debug_execution_id,monitor_id,event_type,rule_version,ratio,forwarded,reason,create_time)
						VALUES (?,?,?,?,?,?,?,?,?,NOW(3))
						""",
				id(), callback.eventId(), context.debugExecutionId(), route.routeId(), callback.eventType().name(),
				route.routeVersion(), route.deductionRatio(), forwarded, decisionReason);
		if (forwarded && route.downstreamCallbackUrl() != null && !route.downstreamCallbackUrl().isBlank()) {
			jdbcTemplate.update("""
					UPDATE scx_ocpx_protocol_snapshot
					SET expires_at=GREATEST(expires_at,DATE_ADD(NOW(3),INTERVAL 1 DAY))
					WHERE feedback_event_id=?
					""", context.feedbackEventId());
			jdbcTemplate.update(
					"""
							INSERT IGNORE INTO scx_ocpx_delivery_job
							(id,event_id,source_event_id,direction,adapter_code,status,attempt_count,next_retry_at,callback_ref,debug_execution_id,
							 create_time,update_time)
							VALUES (?,?,?,'CALLBACK_DOWNSTREAM',?,'PENDING',0,NOW(3),?,?,NOW(3),NOW(3))
							""",
					id(), callback.eventId(), context.feedbackEventId(), route.channelAdapterCode(),
					context.callbackRef(), context.debugExecutionId());
		}
		if (context.debugExecutionId() == null) {
			upsertCallbackMetric(callback, route, forwarded);
		}
		completeDebug(context.debugExecutionId(),
				forwarded && StringUtils.hasText(route.downstreamCallbackUrl()) ? "WAITING_ASYNC" : "SUCCEEDED",
				"CALLBACK_PERSISTED", "回调事实与扣量决策已完成");
		return true;
	}

	@Transactional
	public List<DeliveryJob> claimDeliveryJobs(int limit, String direction) {
		List<DeliveryJob> jobs = jdbcTemplate.query("""
				SELECT id,event_id,source_event_id,direction,adapter_code,callback_ref,attempt_count,debug_execution_id
				FROM scx_ocpx_delivery_job
				WHERE status IN ('PENDING','RETRY') AND direction=? AND next_retry_at<=NOW(3)
				  AND (lease_until IS NULL OR lease_until<NOW(3))
				ORDER BY next_retry_at,id
				LIMIT ? FOR UPDATE SKIP LOCKED
				""", (rs, rowNum) -> new DeliveryJob(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8)), direction, limit);
		for (DeliveryJob job : jobs) {
			jdbcTemplate.update(
					"""
							UPDATE scx_ocpx_delivery_job
							SET status='PROCESSING',lease_owner=?,lease_until=DATE_ADD(NOW(3),INTERVAL 60 SECOND),update_time=NOW(3)
							WHERE id=?
							""",
					leaseOwner, job.id());
		}
		return jobs;
	}

	public DeliveryContext loadDeliveryContext(DeliveryJob job) {
		try {
			return jdbcTemplate.queryForObject("""
					SELECT s.feedback_ciphertext,s.route_ciphertext,e.event_type,e.occurred_at,e.amount,e.amount_raw
					FROM scx_ocpx_protocol_snapshot s
					LEFT JOIN scx_ocpx_event e ON e.event_id=?
					WHERE s.feedback_event_id=?
					""", (rs, rowNum) -> {
				FeedbackReqV1 feedback = decrypt(rs.getString(1), FeedbackReqV1.class);
				RouteSnapshot route = decrypt(rs.getString(2), RouteSnapshot.class);
				NormalizedCallbackEvent callback = null;
				if ("CALLBACK_DOWNSTREAM".equals(job.direction())) {
					callback = new NormalizedCallbackEvent(job.eventId(), job.sourceEventId(), job.callbackRef(),
							OcpxEventType.valueOf(rs.getString(3)),
							rs.getTimestamp(4).toInstant().atOffset(java.time.ZoneOffset.UTC), rs.getBigDecimal(5),
							rs.getString(6));
				}
				return new DeliveryContext(job, route, feedback, callback);
			}, job.eventId(), job.sourceEventId());
		}
		catch (EmptyResultDataAccessException e) {
			throw new IllegalArgumentException("delivery snapshot is missing or expired");
		}
	}

	@Transactional
	public void markDeliverySuccess(DeliveryJob job, int statusCode) {
		markDeliverySuccess(job, statusCode, DeliveryAttemptAudit.empty());
	}

	@Transactional
	public void markDeliverySuccess(DeliveryJob job, int statusCode, DeliveryAttemptAudit audit) {
		int attempt = job.attemptCount() + 1;
		int updated = jdbcTemplate.update(
				"""
						UPDATE scx_ocpx_delivery_job
						SET status='SUCCESS',attempt_count=?,lease_owner=NULL,lease_until=NULL,last_error=NULL,update_time=NOW(3)
						WHERE id=? AND status='PROCESSING' AND lease_owner=?
						""",
				attempt, job.id(), leaseOwner);
		if (updated == 0) {
			return;
		}
		insertAttempt(job, attempt, "SUCCESS", statusCode, null, audit);
		if ("CALLBACK_DOWNSTREAM".equals(job.direction()) && job.debugExecutionId() == null) {
			incrementDeliveryMetric(job.eventId(), "forwarded_conversion_count");
		}
		completeDebug(job.debugExecutionId(), "SUCCEEDED", "DELIVERY_SUCCESS", "目标接口返回成功");
	}

	@Transactional
	public void markDeliveryFailure(DeliveryJob job, Integer statusCode, String error) {
		markDeliveryFailure(job, statusCode, error, DeliveryAttemptAudit.empty());
	}

	@Transactional
	public void markDeliveryFailure(DeliveryJob job, Integer statusCode, String error, DeliveryAttemptAudit audit) {
		int attempt = job.attemptCount() + 1;
		boolean dead = attempt >= properties.getMaxDeliveryAttempts();
		long delaySeconds = switch (attempt) {
			case 1 -> 60;
			case 2 -> 120;
			case 3 -> 300;
			default -> 600;
		};
		int updated = jdbcTemplate.update("""
				UPDATE scx_ocpx_delivery_job
				SET status=?,attempt_count=?,next_retry_at=DATE_ADD(NOW(3),INTERVAL ? SECOND),
				    lease_owner=NULL,lease_until=NULL,last_error=?,update_time=NOW(3)
				WHERE id=? AND status='PROCESSING' AND lease_owner=?
				""", dead ? "DEAD" : "RETRY", attempt, delaySeconds, truncate(error, 1000), job.id(), leaseOwner);
		if (updated == 0) {
			return;
		}
		insertAttempt(job, attempt, dead ? "DEAD" : "FAILED", statusCode, error, audit);
		if (dead && job.debugExecutionId() == null) {
			incrementDeliveryMetric(job.eventId(), "delivery_failure_count");
		}
		completeDebug(job.debugExecutionId(), dead ? "PARTIAL" : "WAITING_ASYNC",
				dead ? "DELIVERY_DEAD" : "DELIVERY_RETRY", dead ? "投递达到最大次数" : "投递失败，已进入重试");
	}

	@Transactional
	public void persistIngressDlq(int partition, String streamRecordId, String eventId, String envelope,
			String reason) {
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_ingress_dlq
				(id,partition_no,stream_record_id,event_id,envelope,reason,status,create_time)
				VALUES (?,?,?,?,?,?,'PENDING',NOW(3))
				""", id(), partition, streamRecordId, eventId, envelope, truncate(reason, 1000));
	}

	private void insertAttempt(DeliveryJob job, int attempt, String status, Integer statusCode, String error,
			DeliveryAttemptAudit audit) {
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_delivery_attempt
						(id,job_id,debug_execution_id,attempt_no,status,http_status,error_message,request_method,target_host,target_path,
						 request_summary,response_excerpt,duration_ms,create_time)
						VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
						""",
				id(), job.id(), job.debugExecutionId(), attempt, status, statusCode, truncate(error, 1000),
				audit.requestMethod(), audit.targetHost(), truncate(audit.targetPath(), 1000),
				truncate(audit.requestSummary(), 4000), truncate(audit.responseExcerpt(), 2000), audit.durationMs());
	}

	private void incrementDeliveryMetric(String eventId, String metricColumn) {
		if (!("forwarded_conversion_count".equals(metricColumn) || "delivery_failure_count".equals(metricColumn))) {
			throw new IllegalArgumentException("unsupported delivery metric");
		}
		List<DeliveryMetricTarget> targets = jdbcTemplate.query("""
				SELECT occurred_at,channel_id,advertiser_id,app_link_id,monitor_id
				FROM scx_ocpx_event WHERE event_id=?
				""", (rs, rowNum) -> new DeliveryMetricTarget(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2),
				rs.getString(3), rs.getString(4), rs.getString(5)), eventId);
		if (targets.isEmpty()) {
			return;
		}
		DeliveryMetricTarget target = targets.get(0);
		LocalDateTime hour = target.occurredAt().truncatedTo(ChronoUnit.HOURS);
		jdbcTemplate.update("""
				INSERT INTO scx_adx_hourly_report
				(id,time_hour,channel_id,advertiser_id,app_link_id,monitor_id,%s)
				VALUES (?,?,?,?,?,?,1)
				ON DUPLICATE KEY UPDATE %s=%s+1
				""".formatted(metricColumn, metricColumn, metricColumn), id(), hour, target.channelId(),
				target.advertiserId(), target.appLinkId(), target.monitorId());
	}

	private void completeDebug(String executionId, String status, String phase, String message) {
		if (!StringUtils.hasText(executionId)) {
			return;
		}
		jdbcTemplate.update("UPDATE scx_api_debug_execution SET status=?,update_time=NOW(3) WHERE id=?", status,
				executionId);
		for (int attempt = 0; attempt < 4; attempt++) {
			Long sequence = jdbcTemplate.queryForObject(
					"SELECT COALESCE(MAX(sequence_no),0)+1 FROM scx_api_debug_execution_log WHERE execution_id=?",
					Long.class, executionId);
			try {
				jdbcTemplate.update("""
						INSERT INTO scx_api_debug_execution_log
						(id,execution_id,sequence_no,source,level,phase,message,detail_json,create_time)
						VALUES (?,?,?,'ENGINE','INFO',?,?,NULL,NOW(3))
						""", id(), executionId, sequence == null ? 1 : sequence, phase, truncate(message, 2000));
				break;
			}
			catch (org.springframework.dao.DuplicateKeyException error) {
				if (attempt == 3) {
					LOGGER.warn("API debug structured log sequence remained contended: phase={}", phase);
				}
			}
		}
	}

	private MDC.MDCCloseable openDebugMdc(String executionId) {
		return MDC.putCloseable("debugExecutionId", StringUtils.hasText(executionId) ? executionId : "-");
	}

	private void upsertFeedbackMetric(FeedbackEnvelope envelope, RouteSnapshot route, BigDecimal costAmount) {
		LocalDateTime hour = envelope.occurredAt()
			.atZoneSameInstant(REPORT_ZONE)
			.toLocalDateTime()
			.truncatedTo(ChronoUnit.HOURS);
		LogDataProjection projection = new LogDataProjection(hour, route.channelId(), route.advertiserId(),
				route.appLinkId(), route.routeId(), envelope.eventType() == OcpxEventType.SHOW ? 1 : 0,
				envelope.eventType() == OcpxEventType.CLICK ? 1 : 0, 0, 0, 0, 0, costAmount, BigDecimal.ZERO);
		jdbcTemplate.update("""
				INSERT INTO scx_adx_hourly_report
				(id,time_hour,channel_id,advertiser_id,app_link_id,monitor_id,impressions,clicks,cost_amount)
				VALUES (?,?,?,?,?,?,?,?,?)
				ON DUPLICATE KEY UPDATE impressions=impressions+VALUES(impressions),clicks=clicks+VALUES(clicks),
				 cost_amount=cost_amount+VALUES(cost_amount)
				""", id(), projection.timeBucket(), projection.channelId(), projection.advertiserId(),
				projection.appLinkId(), projection.monitorId(), projection.impressions(), projection.clicks(),
				projection.costAmount());
	}

	private void upsertCallbackMetric(NormalizedCallbackEvent callback, RouteSnapshot route, boolean forwarded) {
		LocalDateTime hour = callback.actionTime()
			.atZoneSameInstant(REPORT_ZONE)
			.toLocalDateTime()
			.truncatedTo(ChronoUnit.HOURS);
		String eventColumn = metricColumn(callback.eventType());
		LogDataProjection projection = new LogDataProjection(hour, route.channelId(), route.advertiserId(),
				route.appLinkId(), route.routeId(), 0, 0, 1, 0, forwarded ? 0 : 1, 0, BigDecimal.ZERO, BigDecimal.ZERO);
		String sql = """
				INSERT INTO scx_adx_hourly_report
				(id,time_hour,channel_id,advertiser_id,app_link_id,monitor_id,%s,raw_conversion_count,deducted_conversion_count)
				VALUES (?,?,?,?,?,?,1,?,?)
				ON DUPLICATE KEY UPDATE %s=%s+1,raw_conversion_count=raw_conversion_count+VALUES(raw_conversion_count),
				 deducted_conversion_count=deducted_conversion_count+VALUES(deducted_conversion_count)
				"""
			.formatted(eventColumn, eventColumn, eventColumn);
		jdbcTemplate.update(sql, id(), projection.timeBucket(), projection.channelId(), projection.advertiserId(),
				projection.appLinkId(), projection.monitorId(), projection.rawConversions(),
				projection.deductedConversions());
	}

	private String metricColumn(OcpxEventType eventType) {
		return switch (eventType) {
			case ACTIVATE -> "activate_count";
			case EVOKE, RETENTION, REACTIVATION, FIRST_REACTIVATION -> "evoke_count";
			case LOGIN -> "login_count";
			case REGISTER -> "register_count";
			case NEXT_DAY_RETENTION -> "next_stay_count";
			case PAYMENT -> "payment_count";
			case CUSTOMER_ACQUISITION -> "acquisition_count";
			case VISIT -> "visit_count";
			case AUTHENTICATION -> "authentication_count";
			case ORDER -> "order_count";
			case FIRST_ORDER -> "first_order_count";
			case COMPLETE_ORDER -> "finish_order_count";
			case SHOW, CLICK -> throw new IllegalArgumentException("feedback event cannot be aggregated as callback");
		};
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private <T> T decrypt(String ciphertext, Class<T> type) {
		try {
			return objectMapper.readValue(protocolCrypto.decrypt(ciphertext), type);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("encrypted protocol snapshot is invalid", e);
		}
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private record DeliveryMetricTarget(LocalDateTime occurredAt, String channelId, String advertiserId,
			String appLinkId, String monitorId) {
	}

}
