package com.yzytech.scx.ocpx.runtime.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.BillingPort;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
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
import java.time.LocalDate;
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
			int inserted = jdbcTemplate.update(
					"""
							INSERT IGNORE INTO scx_ocpx_event
							(id,event_id,parent_event_id,source_direction,event_type,tenant_id,channel_id,advertiser_id,product_id,
							 app_link_id,task_id,media_account_id,operator_user_id,monitor_id,route_version_id,route_version,
							 dimension_status,occurred_at,received_at,device_platform,device_id_type,device_key_version,
							 device_key_hash,payload_hash,status,callback_ref,
							 debug_execution_id,is_debug)
							VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
							""",
					id(), envelope.eventId(), null, "DOWNSTREAM_FEEDBACK", envelope.eventType().name(),
					route.tenantId(), route.channelId(), route.advertiserId(), route.productId(), route.appLinkId(),
					route.taskId(), route.mediaAccountId(), route.operatorUserId(), route.routeId(),
					route.routeVersionId(), route.routeVersion(), hasFrozenDimensions(route) ? "FROZEN" : "UNMAPPED",
					Timestamp.from(envelope.occurredAt().toInstant()),
					Timestamp.from(envelope.receivedAt().toInstant()), envelope.devicePlatform(),
					envelope.deviceIdType(), envelope.deviceKeyVersion(), envelope.deviceKeyHash(),
					envelope.payloadHash(), "PERSISTED", envelope.callbackRef(), envelope.debugExecutionId(),
					envelope.debugExecutionId() == null ? 0 : 1);
			if (inserted == 0) {
				completeDebug(envelope.debugExecutionId(), "SUCCEEDED", "DUPLICATE", "幂等事件已存在，未重复写入");
				return;
			}
			recordRuntimeFeedbackFact(envelope, route);
			ClickDedupDecision clickDedup = decideClickDedup(envelope, route);
			if (clickDedup.duplicate()) {
				jdbcTemplate.update("""
						UPDATE scx_ocpx_event SET status='DEDUPLICATED',callback_ref=NULL
						WHERE event_id=? AND source_direction='DOWNSTREAM_FEEDBACK'
						""", envelope.eventId());
				if (envelope.debugExecutionId() == null) {
					recordFeedbackContribution(envelope, route, clickDedup, BigDecimal.ZERO);
				}
				completeDebug(envelope.debugExecutionId(), "SUCCEEDED", "CLICK_DEDUPLICATED",
						"点击事实已保存，设备窗口内不计费且不向上游投递");
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
				recordFeedbackContribution(envelope, route, clickDedup,
						billing.posted() ? route.unitPrice() : BigDecimal.ZERO);
			}
			String status = envelope.forwardUpstream() && billing.permitsDelivery() ? "WAITING_ASYNC"
					: billing.permitsDelivery() ? "SUCCEEDED" : "PARTIAL";
			completeDebug(envelope.debugExecutionId(), status, "PERSISTED", "反馈事实与账务处理已完成");
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot persist feedback event", e);
		}
	}

	private ClickDedupDecision decideClickDedup(FeedbackEnvelope envelope, RouteSnapshot route) {
		if (envelope.eventType() != OcpxEventType.CLICK) {
			return ClickDedupDecision.notApplicable();
		}
		int windowSeconds = Math.max(0, route.clickDedupWindowSeconds());
		if (envelope.debugExecutionId() != null) {
			return recordClickDecision(envelope, route, windowSeconds, "ELIGIBLE", "DEBUG_EVENT", envelope.eventId(),
					false);
		}
		if (!StringUtils.hasText(envelope.deviceKeyHash())) {
			return recordClickDecision(envelope, route, windowSeconds, "SKIPPED_NO_DEVICE", "NO_DEVICE_ID",
					envelope.eventId(), false);
		}
		if (windowSeconds == 0) {
			return recordClickDecision(envelope, route, windowSeconds, "ELIGIBLE", "WINDOW_DISABLED",
					envelope.eventId(), false);
		}
		int inserted = jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_click_dedup_state
				(monitor_id,device_key_hash,canonical_event_id,canonical_received_at,create_time,update_time)
				VALUES (?,?,?,?,NOW(3),NOW(3))
				""", route.routeId(), envelope.deviceKeyHash(), envelope.eventId(),
				Timestamp.from(envelope.receivedAt().toInstant()));
		if (inserted == 1) {
			return recordClickDecision(envelope, route, windowSeconds, "ELIGIBLE", "FIRST_IN_WINDOW",
					envelope.eventId(), false);
		}
		ClickDedupState state = jdbcTemplate.queryForObject("""
				SELECT canonical_event_id,canonical_received_at
				FROM scx_ocpx_click_dedup_state
				WHERE monitor_id=? AND device_key_hash=? FOR UPDATE
				""",
				(rs, rowNum) -> new ClickDedupState(rs.getString(1),
						rs.getTimestamp(2).toInstant().atOffset(java.time.ZoneOffset.UTC)),
				route.routeId(), envelope.deviceKeyHash());
		if (state == null) {
			throw new IllegalStateException("click dedup state disappeared during transaction");
		}
		if (!envelope.receivedAt().isBefore(state.receivedAt().plusSeconds(windowSeconds))) {
			jdbcTemplate.update("""
					UPDATE scx_ocpx_click_dedup_state
					SET canonical_event_id=?,canonical_received_at=?,update_time=NOW(3)
					WHERE monitor_id=? AND device_key_hash=?
					""", envelope.eventId(), Timestamp.from(envelope.receivedAt().toInstant()), route.routeId(),
					envelope.deviceKeyHash());
			return recordClickDecision(envelope, route, windowSeconds, "ELIGIBLE", "WINDOW_EXPIRED", envelope.eventId(),
					false);
		}
		return recordClickDecision(envelope, route, windowSeconds, "DEDUPLICATED", "WITHIN_WINDOW", state.eventId(),
				true);
	}

	private ClickDedupDecision recordClickDecision(FeedbackEnvelope envelope, RouteSnapshot route, int windowSeconds,
			String status, String reason, String canonicalEventId, boolean duplicate) {
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_click_dedup_decision
						(event_id,monitor_id,device_key_hash,window_seconds,status,reason,canonical_event_id,received_at,create_time)
						VALUES (?,?,?,?,?,?,?,?,NOW(3))
						""",
				envelope.eventId(), route.routeId(), envelope.deviceKeyHash(), windowSeconds, status, reason,
				canonicalEventId, Timestamp.from(envelope.receivedAt().toInstant()));
		return new ClickDedupDecision(duplicate, status);
	}

	private record ClickDedupState(String eventId, OffsetDateTime receivedAt) {
	}

	private record ClickDedupDecision(boolean duplicate, String status) {

		private static ClickDedupDecision notApplicable() {
			return new ClickDedupDecision(false, "NOT_APPLICABLE");
		}

		private long eligible() {
			return "ELIGIBLE".equals(status) ? 1 : 0;
		}

		private long deduplicated() {
			return "DEDUPLICATED".equals(status) ? 1 : 0;
		}

		private long skipped() {
			return "SKIPPED_NO_DEVICE".equals(status) ? 1 : 0;
		}

		private long classified() {
			return "NOT_APPLICABLE".equals(status) ? 0 : 1;
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
	public CallbackPersistenceResult persistCallback(NormalizedCallbackEvent callback, CallbackContext context,
			boolean forwarded, String decisionReason) {
		RouteSnapshot route = context.route();
		if (callbackExists(callback.eventId())) {
			return new CallbackPersistenceResult("DUPLICATE");
		}
		if (forwarded) {
			if (route.eventSnapshotVersion() == 0 || context.debugExecutionId() != null) {
				String limitResult = context.debugExecutionId() == null ? "NOT_APPLICABLE" : "DEBUG_BYPASS";
				if (!insertCallbackEvent(callback, context, "PERSISTED", BigDecimal.ZERO, limitResult)) {
					return new CallbackPersistenceResult("DUPLICATE");
				}
				insertDeductionDecision(callback, context, true, decisionReason);
				createDownstreamJob(callback, context);
			}
			else {
				LocalDate businessDate = LocalDate.now(REPORT_ZONE);
				jdbcTemplate.update("""
						INSERT INTO scx_ocpx_event_daily_quota
						(monitor_id,upstream_event_code,business_date,queued_count,create_time,update_time)
						VALUES (?,?,?,0,NOW(3),NOW(3))
						ON DUPLICATE KEY UPDATE queued_count=queued_count
						""", route.routeId(), callback.mappedUpstreamEventCode(), businessDate);
				Integer queued = jdbcTemplate.queryForObject("""
						SELECT queued_count FROM scx_ocpx_event_daily_quota
						WHERE monitor_id=? AND upstream_event_code=? AND business_date=? FOR UPDATE
						""", Integer.class, route.routeId(), callback.mappedUpstreamEventCode(), businessDate);
				if (callbackExists(callback.eventId())) {
					return new CallbackPersistenceResult("DUPLICATE");
				}
				if (callback.dailyLimit() > 0 && queued != null && queued >= callback.dailyLimit()) {
					return new CallbackPersistenceResult("LIMIT_EXCEEDED");
				}
				BillingPort.BillingResult billing = billingPort.charge(callback.eventId(), route.advertiserId(),
						callback.assessmentUnitPrice(), "CNY", "CPA");
				annotateCpaBilling(callback, route);
				if (!billing.permitsDelivery()) {
					return new CallbackPersistenceResult("INSUFFICIENT_BALANCE");
				}
				if (!insertCallbackEvent(callback, context, "PERSISTED", callback.assessmentUnitPrice(), "ACCEPTED")) {
					return new CallbackPersistenceResult("DUPLICATE");
				}
				insertDeductionDecision(callback, context, true, decisionReason);
				jdbcTemplate.update("""
						UPDATE scx_ocpx_event_daily_quota SET queued_count=queued_count+1,update_time=NOW(3)
						WHERE monitor_id=? AND upstream_event_code=? AND business_date=?
						""", route.routeId(), callback.mappedUpstreamEventCode(), businessDate);
				if (!createDownstreamJob(callback, context)) {
					throw new IllegalStateException("published callback route has no downstream delivery target");
				}
			}
		}
		else {
			if (!insertCallbackEvent(callback, context, "DEDUCTED", BigDecimal.ZERO, "SKIPPED_DEDUCTION")) {
				return new CallbackPersistenceResult("DUPLICATE");
			}
			insertDeductionDecision(callback, context, false, decisionReason);
		}
		if (context.debugExecutionId() == null) {
			recordCallbackContribution(callback, route, forwarded);
		}
		boolean waitingForDelivery = forwarded && StringUtils.hasText(route.downstreamCallbackUrl());
		completeDebug(context.debugExecutionId(), waitingForDelivery ? "WAITING_ASYNC" : "SUCCEEDED",
				"CALLBACK_PERSISTED", forwarded ? "回调事实与媒体任务已创建" : "扣量事实已保存，未计费且未创建媒体任务");
		return new CallbackPersistenceResult("ACCEPTED");
	}

	private boolean callbackExists(String eventId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_event WHERE source_direction='UPSTREAM_CALLBACK' AND event_id=?
				""", Long.class, eventId);
		return count != null && count > 0;
	}

	private boolean insertCallbackEvent(NormalizedCallbackEvent callback, CallbackContext context, String status,
			BigDecimal cpaCharge, String dailyLimitResult) {
		RouteSnapshot route = context.route();
		int inserted = jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_event
						(id,event_id,parent_event_id,source_direction,event_type,source_event_code,conversion_event_code,media_event_code,
						 tenant_id,channel_id,advertiser_id,product_id,app_link_id,task_id,media_account_id,operator_user_id,
						 monitor_id,route_version_id,route_version,dimension_status,occurred_at,received_at,payload_hash,status,
						 callback_ref,amount,amount_raw,
						 cpa_charge_amount,daily_limit_result,debug_execution_id,is_debug)
						VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
						""",
				id(), callback.eventId(), context.feedbackEventId(), "UPSTREAM_CALLBACK", callback.eventType().name(),
				callback.sourceEventCode(), callback.conversionEventCode(), callback.mediaEventCode(), route.tenantId(),
				route.channelId(), route.advertiserId(), route.productId(), route.appLinkId(), route.taskId(),
				route.mediaAccountId(), route.operatorUserId(), route.routeId(), route.routeVersionId(),
				route.routeVersion(), hasFrozenDimensions(route) ? "FROZEN" : "UNMAPPED",
				Timestamp.from(callback.actionTime().toInstant()), Timestamp.from(OffsetDateTime.now().toInstant()),
				callback.eventId(), status, context.callbackRef(), callback.amount(), callback.amountRaw(), cpaCharge,
				dailyLimitResult, context.debugExecutionId(), context.debugExecutionId() == null ? 0 : 1);
		if (inserted == 1) {
			recordRuntimeCallbackFact(callback, context, status);
		}
		return inserted == 1;
	}

	private void annotateCpaBilling(NormalizedCallbackEvent callback, RouteSnapshot route) {
		jdbcTemplate.update(
				"""
						UPDATE scx_ocpx_billing_entry
						SET channel_id=?,app_link_id=?,monitor_id=?,source_event_code=?,conversion_event_code=?,media_event_code=?
						WHERE charge_event_id=? AND advertiser_id=? AND mode='CPA'
						""",
				route.channelId(), route.appLinkId(), route.routeId(), callback.sourceEventCode(),
				callback.conversionEventCode(), callback.mediaEventCode(), callback.eventId(), route.advertiserId());
	}

	private void insertDeductionDecision(NormalizedCallbackEvent callback, CallbackContext context, boolean forwarded,
			String decisionReason) {
		RouteSnapshot route = context.route();
		jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_deduction_decision
						(id,callback_event_id,debug_execution_id,monitor_id,event_type,rule_version,ratio,forwarded,reason,create_time)
						VALUES (?,?,?,?,?,?,?,?,?,NOW(3))
						""",
				id(), callback.eventId(), context.debugExecutionId(), route.routeId(), callback.eventType().name(),
				route.routeVersion(), route.deductionRatio(), forwarded, decisionReason);
	}

	private boolean createDownstreamJob(NormalizedCallbackEvent callback, CallbackContext context) {
		RouteSnapshot route = context.route();
		if (route.downstreamCallbackUrl() == null || route.downstreamCallbackUrl().isBlank()) {
			return false;
		}
		jdbcTemplate.update("""
				UPDATE scx_ocpx_protocol_snapshot
				SET expires_at=GREATEST(expires_at,DATE_ADD(NOW(3),INTERVAL 1 DAY))
				WHERE feedback_event_id=?
				""", context.feedbackEventId());
		int inserted = jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_delivery_job
						(id,event_id,source_event_id,direction,adapter_code,status,attempt_count,next_retry_at,callback_ref,debug_execution_id,
						 create_time,update_time)
						VALUES (?,?,?,'CALLBACK_DOWNSTREAM',?,'PENDING',0,NOW(3),?,?,NOW(3),NOW(3))
						""",
				id(), callback.eventId(), context.feedbackEventId(), route.channelAdapterCode(), context.callbackRef(),
				context.debugExecutionId());
		return inserted == 1;
	}

	public record CallbackPersistenceResult(String status) {
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
					SELECT s.feedback_ciphertext,s.route_ciphertext,e.event_type,e.occurred_at,e.amount,e.amount_raw,
					 e.source_event_code,e.conversion_event_code,e.media_event_code,e.cpa_charge_amount
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
							rs.getString(6), rs.getString(7), rs.getString(7), rs.getString(8), rs.getString(9),
							rs.getBigDecimal(10), 0);
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
		recordRuntimeDeliveryFact(job, attempt, "SUCCEEDED", "DELIVERY_SUCCEEDED");
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
		recordRuntimeDeliveryFact(job, attempt, dead ? "DLQ" : "FAILED",
				dead ? "DELIVERY_ATTEMPTS_EXHAUSTED" : "DELIVERY_RETRY_WAIT");
		if (dead) {
			recordRuntimeDeliveryFailure(job, error);
		}
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
		recordRuntimeIngressFailure(partition, streamRecordId, eventId, reason);
	}

	private void recordRuntimeFeedbackFact(FeedbackEnvelope envelope, RouteSnapshot route) {
		LocalDateTime received = envelope.receivedAt().atZoneSameInstant(REPORT_ZONE).toLocalDateTime();
		LocalDateTime event = envelope.occurredAt().atZoneSameInstant(REPORT_ZONE).toLocalDateTime();
		if (event.isAfter(received)) {
			event = received;
		}
		jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_runtime_fact
						(id,source_type,source_id,fact_type,direction,acceptance_status,processing_status,tenant_id,
						 advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
						 route_id,route_version_id,route_version,event_code,event_snapshot_schema_version,event_time,
						 received_time,request_id_hash,correlation_id_hash,external_click_id_hash,idempotency_key_hash,
						 payload_hash,summary_code,dimension_status,quality_status,create_time)
						VALUES (?,'FEEDBACK',?,?, 'INGRESS','ACCEPTED','SUCCEEDED',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
						""",
				id(), envelope.eventId(), envelope.eventType() == OcpxEventType.CLICK ? "CLICK" : "INTEGRATION",
				route.tenantId(), route.advertiserId(), route.productId(), route.appLinkId(), route.taskId(),
				route.channelId(), route.mediaAccountId(), route.operatorUserId(), route.routeId(),
				route.routeVersionId(), route.routeVersion(), envelope.eventType().name(), route.eventSnapshotVersion(),
				event, received, protocolCrypto.blindIndex("S10_RUNTIME_EXACT", envelope.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", envelope.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", envelope.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_FACT", "FEEDBACK|" + envelope.eventId()), envelope.payloadHash(),
				"FEEDBACK_PERSISTED", hasFrozenDimensions(route) ? "FROZEN" : "UNMAPPED",
				hasFrozenDimensions(route) ? "COMPLETE" : "UNMAPPED");
	}

	private void recordRuntimeCallbackFact(NormalizedCallbackEvent callback, CallbackContext context, String status) {
		RouteSnapshot route = context.route();
		LocalDateTime received = LocalDateTime.now(REPORT_ZONE);
		LocalDateTime event = callback.actionTime().atZoneSameInstant(REPORT_ZONE).toLocalDateTime();
		if (event.isAfter(received)) {
			event = received;
		}
		jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_runtime_fact
						(id,source_type,source_id,fact_type,direction,acceptance_status,processing_status,tenant_id,
						 advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
						 route_id,route_version_id,route_version,event_code,event_snapshot_schema_version,event_time,
						 received_time,request_id_hash,correlation_id_hash,external_click_id_hash,idempotency_key_hash,
						 payload_hash,summary_code,dimension_status,quality_status,create_time)
						VALUES (?,'CALLBACK',?,'CONVERSION','INGRESS','ACCEPTED','SUCCEEDED',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
						""",
				id(), callback.eventId(), route.tenantId(), route.advertiserId(), route.productId(), route.appLinkId(),
				route.taskId(), route.channelId(), route.mediaAccountId(), route.operatorUserId(), route.routeId(),
				route.routeVersionId(), route.routeVersion(), callback.conversionEventCode(),
				route.eventSnapshotVersion(), event, received,
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", callback.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", context.feedbackEventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", context.feedbackEventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_FACT", "CALLBACK|" + callback.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_PAYLOAD", callback.eventId()),
				"DEDUCTED".equals(status) ? "CALLBACK_DEDUCTED" : "CALLBACK_PERSISTED",
				hasFrozenDimensions(route) ? "FROZEN" : "UNMAPPED",
				hasFrozenDimensions(route) ? "COMPLETE" : "UNMAPPED");
	}

	private void recordRuntimeDeliveryFact(DeliveryJob job, int attempt, String processingStatus, String summaryCode) {
		jdbcTemplate.update(
				"""
						INSERT IGNORE INTO scx_ocpx_runtime_fact
						(id,source_type,source_id,fact_type,direction,acceptance_status,processing_status,tenant_id,
						 advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
						 route_id,route_version_id,route_version,event_code,event_snapshot_schema_version,event_time,
						 received_time,correlation_id_hash,idempotency_key_hash,summary_code,dimension_status,quality_status,create_time)
						SELECT ?,'DELIVERY',?,'DELIVERY_ATTEMPT',?,'ACCEPTED',?,e.tenant_id,e.advertiser_id,e.product_id,
						 e.app_link_id,e.task_id,e.channel_id,e.media_account_id,e.operator_user_id,e.monitor_id,
						 e.route_version_id,e.route_version,e.event_type,rv.event_snapshot_schema_version,
						 LEAST(e.occurred_at,NOW(3)),NOW(3),?,?,?,e.dimension_status,
						 IF(e.dimension_status='FROZEN','COMPLETE','UNMAPPED'),NOW(3)
						FROM scx_ocpx_event e LEFT JOIN scx_ocpx_route_version rv ON rv.id=e.route_version_id
						WHERE e.event_id=? LIMIT 1
						""",
				id(), job.id() + ":" + attempt, "FEEDBACK_UPSTREAM".equals(job.direction()) ? "UPSTREAM" : "DOWNSTREAM",
				processingStatus, protocolCrypto.blindIndex("S10_RUNTIME_EXACT", job.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_FACT", "DELIVERY|" + job.id() + "|" + attempt), summaryCode,
				job.eventId());
	}

	private void recordRuntimeDeliveryFailure(DeliveryJob job, String error) {
		String failureId = id();
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_runtime_failure
				(id,source_fact_id,source_type,source_id,failure_class,error_code,replayable,failure_revision,
				 tenant_id,advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
				 route_id,route_version_id,route_version,event_snapshot_schema_version,event_time,received_time,
				 correlation_id_hash,idempotency_key_hash,payload_hash,summary_hash,dimension_status,create_time)
				SELECT ?,NULL,'DELIVERY',?,'RETRYABLE','DELIVERY_ATTEMPTS_EXHAUSTED',
				 IF(rv.id IS NULL,0,1),1,e.tenant_id,e.advertiser_id,e.product_id,e.app_link_id,e.task_id,e.channel_id,
				 e.media_account_id,e.operator_user_id,e.monitor_id,e.route_version_id,e.route_version,
				 rv.event_snapshot_schema_version,LEAST(e.occurred_at,NOW(3)),NOW(3),?,?,e.payload_hash,?,
				 e.dimension_status,NOW(3)
				FROM scx_ocpx_event e LEFT JOIN scx_ocpx_route_version rv ON rv.id=e.route_version_id
				WHERE e.event_id=? LIMIT 1
				""", failureId, job.id(), protocolCrypto.blindIndex("S10_RUNTIME_EXACT", job.eventId()),
				protocolCrypto.blindIndex("S10_RUNTIME_FAILURE", "DELIVERY|" + job.id()),
				protocolCrypto.blindIndex("S10_RUNTIME_SUMMARY", "DELIVERY_ATTEMPTS_EXHAUSTED|" + safeCode(error)),
				job.eventId());
	}

	private void recordRuntimeIngressFailure(int partition, String streamRecordId, String eventId, String reason) {
		String failureClass = classifyFailure(reason);
		String errorCode = failureCode(reason);
		String idempotency = protocolCrypto.blindIndex("S10_RUNTIME_FAILURE",
				"INGRESS|" + partition + "|" + streamRecordId);
		String summary = protocolCrypto.blindIndex("S10_RUNTIME_SUMMARY", errorCode + "|" + safeCode(reason));
		int inserted = jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_runtime_failure
				(id,source_fact_id,source_type,source_id,failure_class,error_code,replayable,failure_revision,
				 tenant_id,advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,operator_user_id,
				 route_id,route_version_id,route_version,event_snapshot_schema_version,event_time,received_time,
				 correlation_id_hash,idempotency_key_hash,payload_hash,summary_hash,dimension_status,create_time)
				SELECT ?,NULL,'FEEDBACK',?,?,?,IF(rv.id IS NULL OR ?<>'RETRYABLE',0,1),1,e.tenant_id,e.advertiser_id,
				 e.product_id,e.app_link_id,e.task_id,e.channel_id,e.media_account_id,e.operator_user_id,e.monitor_id,
				 e.route_version_id,e.route_version,rv.event_snapshot_schema_version,LEAST(e.occurred_at,NOW(3)),NOW(3),
				 ?,?,e.payload_hash,?,e.dimension_status,NOW(3)
				FROM scx_ocpx_event e LEFT JOIN scx_ocpx_route_version rv ON rv.id=e.route_version_id
				WHERE e.event_id=? LIMIT 1
				""", id(), partition + ":" + streamRecordId, failureClass, errorCode, failureClass,
				protocolCrypto.blindIndex("S10_RUNTIME_EXACT", String.valueOf(eventId)), idempotency, summary, eventId);
		if (inserted == 0) {
			jdbcTemplate.update(
					"""
							INSERT IGNORE INTO scx_ocpx_runtime_failure
							(id,source_type,source_id,failure_class,error_code,replayable,failure_revision,event_time,received_time,
							 correlation_id_hash,idempotency_key_hash,summary_hash,dimension_status,create_time)
							VALUES (?,'FEEDBACK',?,?,?,0,1,NOW(3),NOW(3),?,?,?,'UNMAPPED',NOW(3))
							""",
					id(), partition + ":" + streamRecordId, failureClass, errorCode,
					protocolCrypto.blindIndex("S10_RUNTIME_EXACT", String.valueOf(eventId)), idempotency, summary);
		}
	}

	private String classifyFailure(String reason) {
		String upper = String.valueOf(reason).toUpperCase(java.util.Locale.ROOT);
		if (upper.contains("SIGNATURE") || upper.contains("AUTH"))
			return "SIGNATURE_FAILED";
		if (upper.contains("INVALID") || upper.contains("PROTOCOL"))
			return "PROTOCOL_REJECTED";
		if (upper.contains("DUPLICATE"))
			return "DUPLICATE";
		if (upper.contains("LATE"))
			return "LATE_ARRIVAL";
		if (upper.contains("TIMEOUT") || upper.contains("TEMPORARY") || upper.contains("CONNECTION"))
			return "RETRYABLE";
		return "NON_RETRYABLE";
	}

	private String failureCode(String reason) {
		String code = safeCode(reason);
		return code == null || code.isBlank() ? "UNKNOWN_FAILURE" : code;
	}

	private String safeCode(String value) {
		if (value == null)
			return null;
		String code = value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
		return code.substring(0, Math.min(64, code.length()));
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
				SELECT occurred_at,received_at,tenant_id,channel_id,advertiser_id,product_id,app_link_id,task_id,
				 media_account_id,operator_user_id,monitor_id,route_version_id,route_version,dimension_status
				FROM scx_ocpx_event WHERE event_id=?
				""",
				(rs, rowNum) -> new DeliveryMetricTarget(rs.getTimestamp(1).toLocalDateTime(),
						rs.getTimestamp(2).toLocalDateTime(), rs.getString(3), rs.getString(4), rs.getString(5),
						rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
						rs.getString(11), rs.getString(12), rs.getLong(13), rs.getString(14)),
				eventId);
		if (targets.isEmpty()) {
			return;
		}
		DeliveryMetricTarget target = targets.get(0);
		if (!"FROZEN".equals(target.dimensionStatus())) {
			return;
		}
		String kind = "forwarded_conversion_count".equals(metricColumn) ? "DELIVERY_SUCCESS" : "DELIVERY_FAILURE";
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution
				(id,source_event_id,contribution_kind,tenant_id,advertiser_id,product_id,app_link_id,task_id,
				 channel_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
				 event_time,received_time,stat_hour,timezone,%s)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Asia/Shanghai',1)
				""".formatted(metricColumn), id(), eventId, kind, target.tenantId(), target.advertiserId(),
				target.productId(), target.appLinkId(), target.taskId(), target.channelId(), target.mediaAccountId(),
				target.operatorUserId(), target.monitorId(), target.routeVersionId(), target.routeVersion(),
				target.occurredAt(), target.receivedAt(), target.occurredAt().truncatedTo(ChronoUnit.HOURS));
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

	private void recordFeedbackContribution(FeedbackEnvelope envelope, RouteSnapshot route, ClickDedupDecision decision,
			BigDecimal costAmount) {
		if (!hasFrozenDimensions(route)) {
			return;
		}
		LocalDateTime eventTime = envelope.occurredAt().atZoneSameInstant(REPORT_ZONE).toLocalDateTime();
		LocalDateTime hour = eventTime.truncatedTo(ChronoUnit.HOURS);
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution
				(id,source_event_id,contribution_kind,tenant_id,advertiser_id,product_id,app_link_id,task_id,
				 channel_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
				 event_time,received_time,stat_hour,timezone,impressions,raw_clicks,valid_clicks,duplicate_clicks,
				 skipped_clicks,cost_amount)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Asia/Shanghai',?,?,?,?,?,?)
				""", id(), envelope.eventId(), "FEEDBACK", route.tenantId(), route.advertiserId(), route.productId(),
				route.appLinkId(), route.taskId(), route.channelId(), route.mediaAccountId(), route.operatorUserId(),
				route.routeId(), route.routeVersionId(), route.routeVersion(), eventTime,
				envelope.receivedAt().atZoneSameInstant(REPORT_ZONE).toLocalDateTime(), hour,
				envelope.eventType() == OcpxEventType.SHOW ? 1 : 0, envelope.eventType() == OcpxEventType.CLICK ? 1 : 0,
				decision.eligible(), decision.deduplicated(), decision.skipped(), costAmount);
	}

	private void recordCallbackContribution(NormalizedCallbackEvent callback, RouteSnapshot route, boolean forwarded) {
		if (!hasFrozenDimensions(route)) {
			return;
		}
		LocalDateTime eventTime = callback.actionTime().atZoneSameInstant(REPORT_ZONE).toLocalDateTime();
		LocalDateTime receivedTime = LocalDateTime.now(REPORT_ZONE);
		LocalDateTime hour = eventTime.truncatedTo(ChronoUnit.HOURS);
		String eventColumn = contributionMetricColumn(callback.eventType());
		if (eventColumn == null) {
			jdbcTemplate.update("""
					INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution
					(id,source_event_id,contribution_kind,tenant_id,advertiser_id,product_id,app_link_id,task_id,
					 channel_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
					 event_time,received_time,stat_hour,timezone,raw_conversion_count,deduction_count,
					 deducted_conversion_count,event_counts_json)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Asia/Shanghai',1,?,?,JSON_OBJECT('unavailableEvent',1))
					""", id(), callback.eventId(), "CALLBACK", route.tenantId(), route.advertiserId(),
					route.productId(), route.appLinkId(), route.taskId(), route.channelId(), route.mediaAccountId(),
					route.operatorUserId(), route.routeId(), route.routeVersionId(), route.routeVersion(), eventTime,
					receivedTime, hour, forwarded ? 0 : 1, forwarded ? 0 : 1);
			return;
		}
		String sql = """
				INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution
				(id,source_event_id,contribution_kind,tenant_id,advertiser_id,product_id,app_link_id,task_id,
				 channel_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
				 event_time,received_time,stat_hour,timezone,%s,raw_conversion_count,deduction_count,
				 deducted_conversion_count)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Asia/Shanghai',1,1,?,?)
				""".formatted(eventColumn);
		jdbcTemplate.update(sql, id(), callback.eventId(), "CALLBACK", route.tenantId(), route.advertiserId(),
				route.productId(), route.appLinkId(), route.taskId(), route.channelId(), route.mediaAccountId(),
				route.operatorUserId(), route.routeId(), route.routeVersionId(), route.routeVersion(), eventTime,
				receivedTime, hour, forwarded ? 0 : 1, forwarded ? 0 : 1);
	}

	private String contributionMetricColumn(OcpxEventType eventType) {
		return switch (eventType) {
			case ACTIVATE -> "activation_count";
			case FIRST_ACTIVATION -> "first_activation_count";
			case REACTIVATION -> "reactivation_count";
			case REGISTER -> "registration_count";
			case NEXT_DAY_RETENTION, RETENTION_D2 -> "retention_d2_count";
			case RETENTION_D3 -> "retention_d3_count";
			case RETENTION_D7 -> "retention_d7_count";
			case PAYMENT -> "payment_count";
			case ORDER -> "order_count";
			case FIRST_ORDER -> "first_order_count";
			case EVOKE, RETENTION, FIRST_REACTIVATION, LOGIN, RETENTION_D4, RETENTION_D5, RETENTION_D6,
					CUSTOMER_ACQUISITION, VISIT, AUTHENTICATION, COMPLETE_ORDER, VIDEO_COMPLETE_90S, KEY_ACTION,
					FORM_SUBMIT, KEY_PAGE_VIEW, RECALL, UNKNOWN ->
				null;
			case SHOW, CLICK -> throw new IllegalArgumentException("feedback event cannot be aggregated as callback");
		};
	}

	private boolean hasFrozenDimensions(RouteSnapshot route) {
		return StringUtils.hasText(route.tenantId()) && StringUtils.hasText(route.advertiserId())
				&& StringUtils.hasText(route.productId()) && StringUtils.hasText(route.appLinkId())
				&& StringUtils.hasText(route.taskId()) && StringUtils.hasText(route.channelId())
				&& StringUtils.hasText(route.mediaAccountId()) && StringUtils.hasText(route.operatorUserId())
				&& StringUtils.hasText(route.routeId()) && StringUtils.hasText(route.routeVersionId())
				&& route.routeVersion() > 0;
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

	private record DeliveryMetricTarget(LocalDateTime occurredAt, LocalDateTime receivedAt, String tenantId,
			String channelId, String advertiserId, String productId, String appLinkId, String taskId,
			String mediaAccountId, String operatorUserId, String monitorId, String routeVersionId, long routeVersion,
			String dimensionStatus) {
	}

}
