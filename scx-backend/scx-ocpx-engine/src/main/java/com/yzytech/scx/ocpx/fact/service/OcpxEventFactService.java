package com.yzytech.scx.ocpx.fact.service;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.DeductionDecision;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * MySQL authoritative event, configurable billing and hourly projection transaction.
 *
 * <p>
 * The unique fact key is the final idempotency boundary. Redis may still be used by
 * compatibility paths, but does not decide whether a fact is counted or billed.
 * </p>
 */
@Service
@Slf4j
public class OcpxEventFactService {

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyyMMddHH");

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.UNNECESSARY);

	private static final String HOURLY_UPSERT = """
			INSERT INTO scx_ocpx_hourly_metric (
			  time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,
			  impression_count,click_count,target_conversion_count,callback_count,
			  activate_count,evoke_count,login_count,register_count,payment_count,
			  customer_acquisition_count,next_stay_count,visits_count,authentication_count,
			  place_order_count,finish_the_order_count,first_activation_count,first_order_count,
			  spend_amount,currency_amount,rejected_click_count,rejected_conversion_count
			) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
			ON DUPLICATE KEY UPDATE
			  impression_count=impression_count+VALUES(impression_count),
			  click_count=click_count+VALUES(click_count),
			  target_conversion_count=target_conversion_count+VALUES(target_conversion_count),
			  callback_count=callback_count+VALUES(callback_count),
			  activate_count=activate_count+VALUES(activate_count),
			  evoke_count=evoke_count+VALUES(evoke_count),
			  login_count=login_count+VALUES(login_count),
			  register_count=register_count+VALUES(register_count),
			  payment_count=payment_count+VALUES(payment_count),
			  customer_acquisition_count=customer_acquisition_count+VALUES(customer_acquisition_count),
			  next_stay_count=next_stay_count+VALUES(next_stay_count),
			  visits_count=visits_count+VALUES(visits_count),
			  authentication_count=authentication_count+VALUES(authentication_count),
			  place_order_count=place_order_count+VALUES(place_order_count),
			  finish_the_order_count=finish_the_order_count+VALUES(finish_the_order_count),
			  first_activation_count=first_activation_count+VALUES(first_activation_count),
			  first_order_count=first_order_count+VALUES(first_order_count),
			  spend_amount=spend_amount+VALUES(spend_amount),
			  currency_amount=currency_amount+VALUES(currency_amount),
			  rejected_click_count=rejected_click_count+VALUES(rejected_click_count),
			  rejected_conversion_count=rejected_conversion_count+VALUES(rejected_conversion_count),
			  update_time=CURRENT_TIMESTAMP(3)
			""";

	private final JdbcTemplate jdbcTemplate;

	public OcpxEventFactService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public FeedbackFactResult recordFeedback(FeedbackReq request) {
		requireFeedback(request);
		LocalDateTime receivedAt = LocalDateTime.now(BUSINESS_ZONE);
		TaskSnapshot task = requireTask(request.getTId(), request.getAId(), request.getLId(), request.getMId());
		String eventCode = request.getEvent().getKey();
		String factId = nextId();
		String factKey = factKey(request, eventCode);
		String initialReason = feedbackReason(request.getEvent(), task);
		if (!insertFact(factId, factKey, "FEEDBACK", eventCode, request, task, false, ZERO, ZERO, "NOT_APPLICABLE",
				initialReason, null, receivedAt)) {
			return FeedbackFactResult.duplicate();
		}

		if (request.getEvent() == EventTypeEnum.IMP) {
			upsertMetric(receivedAt, task, MetricDelta.forImpression());
			return new FeedbackFactResult(true, true, "NOT_APPLICABLE", ZERO);
		}
		if (request.getEvent() != EventTypeEnum.CLICK) {
			return new FeedbackFactResult(true, true, "NOT_APPLICABLE", ZERO);
		}
		if (!task.billingEnabled() || task.billingType() != 1) {
			upsertMetric(receivedAt, task, MetricDelta.forClick());
			return new FeedbackFactResult(true, true, "NOT_APPLICABLE", ZERO);
		}

		BigDecimal price = money(task.unitPrice());
		BigDecimal balanceBefore = lockBalance(task.advertiserId());
		if (balanceBefore.compareTo(price) < 0) {
			insertChargeEntry("CLICK_CHARGE", "CLICK", factId, task, price, balanceBefore, balanceBefore, "REJECTED",
					"INSUFFICIENT_BALANCE", receivedAt);
			updateFactBilling(factId, ZERO, "REJECTED_INSUFFICIENT_BALANCE", "INSUFFICIENT_BALANCE");
			upsertMetric(receivedAt, task, MetricDelta.forRejectedClick());
			return new FeedbackFactResult(true, false, "REJECTED_INSUFFICIENT_BALANCE", ZERO);
		}

		BigDecimal balanceAfter = balanceBefore.subtract(price).setScale(6, RoundingMode.UNNECESSARY);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_advertiser_account
				SET available_balance=?,update_time=CURRENT_TIMESTAMP(3)
				WHERE advertiser_id=?
				""", balanceAfter, task.advertiserId());
		insertChargeEntry("CLICK_CHARGE", "CLICK", factId, task, price, balanceBefore, balanceAfter, "POSTED", null,
				receivedAt);
		updateFactBilling(factId, price, "POSTED", null);
		upsertMetric(receivedAt, task, MetricDelta.postedClick(price));
		return new FeedbackFactResult(true, true, "POSTED", price);
	}

	@Transactional
	public CallbackFactResult recordCallback(CallbackReq request) {
		if (request == null || request.getFeedback() == null || request.getActionType() == null) {
			throw new IllegalArgumentException("callback request, feedback and actionType are required");
		}
		FeedbackReq feedback = request.getFeedback();
		requireDimensions(feedback);
		LocalDateTime receivedAt = LocalDateTime.now(BUSINESS_ZONE);
		TaskSnapshot task = requireTask(feedback.getTId(), feedback.getAId(), feedback.getLId(), feedback.getMId());
		String eventCode = request.getActionType().name();
		boolean targetConversion = eventCode.equals(task.conversionEvent());
		BigDecimal currencyAmount = money(request.getCurrencyAmount());
		if (currencyAmount.signum() < 0) {
			throw new IllegalArgumentException("currencyAmount must not be negative");
		}
		String factId = nextId();
		String factKey = factKey(feedback, eventCode);
		String initialReason = callbackReason(targetConversion, task);
		if (!insertFact(factId, factKey, "CALLBACK", eventCode, feedback, task, targetConversion, ZERO, currencyAmount,
				"NOT_APPLICABLE", initialReason, "PENDING", receivedAt)) {
			return existingCallback(factKey);
		}
		if (!targetConversion || !task.billingEnabled() || task.billingType() != 2) {
			upsertMetric(receivedAt, task,
					MetricDelta.conversion(request.getActionType(), targetConversion, currencyAmount, ZERO, false));
			return new CallbackFactResult(true, targetConversion, factId, "PENDING");
		}

		BigDecimal price = money(task.unitPrice());
		BigDecimal balanceBefore = lockBalance(task.advertiserId());
		if (balanceBefore.compareTo(price) < 0) {
			insertChargeEntry("CONVERSION_CHARGE", "CONVERSION", factId, task, price, balanceBefore, balanceBefore,
					"REJECTED", "INSUFFICIENT_BALANCE", receivedAt);
			updateFactBilling(factId, ZERO, "REJECTED_INSUFFICIENT_BALANCE", "INSUFFICIENT_BALANCE");
			upsertMetric(receivedAt, task,
					MetricDelta.conversion(request.getActionType(), true, currencyAmount, ZERO, true));
			log.warn("conversion charge rejected advertiserId:{} taskId:{} factId:{} reason:INSUFFICIENT_BALANCE",
					task.advertiserId(), task.id(), factId);
			return new CallbackFactResult(true, true, factId, "PENDING");
		}
		BigDecimal balanceAfter = balanceBefore.subtract(price).setScale(6, RoundingMode.UNNECESSARY);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_advertiser_account
				SET available_balance=?,update_time=CURRENT_TIMESTAMP(3)
				WHERE advertiser_id=?
				""", balanceAfter, task.advertiserId());
		insertChargeEntry("CONVERSION_CHARGE", "CONVERSION", factId, task, price, balanceBefore, balanceAfter, "POSTED",
				null, receivedAt);
		updateFactBilling(factId, price, "POSTED", null);
		upsertMetric(receivedAt, task,
				MetricDelta.conversion(request.getActionType(), true, currencyAmount, price, false));
		return new CallbackFactResult(true, targetConversion, factId, "PENDING");
	}

	/**
	 * Persist the first completed media deduction decision and its hourly projection.
	 *
	 * <p>
	 * The conditional fact update is the MySQL idempotency boundary. Only its winner
	 * increments the passed or deducted aggregate, so callback retries and concurrent
	 * workers cannot double count or submit the media callback twice.
	 * </p>
	 */
	@Transactional
	public boolean finalizeDeduction(String factId, BigDecimal ratio, DeductionDecision decision) {
		if (!StringUtils.hasText(factId) || decision == null) {
			throw new IllegalArgumentException("factId and deduction decision are required");
		}
		BigDecimal normalizedRatio = money(ratio);
		if (normalizedRatio.compareTo(BigDecimal.ZERO) < 0 || normalizedRatio.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("deduction ratio must be between 0 and 1");
		}
		LocalDateTime decidedAt = LocalDateTime.now(BUSINESS_ZONE);
		int updated = jdbcTemplate.update("""
				UPDATE scx_ocpx_event_fact
				SET deduction_decision=?,deduction_ratio_snapshot=?,deduction_decided_at=?
				WHERE id=? AND flow_type='CALLBACK' AND deduction_decision='PENDING'
				""", decision.name(), normalizedRatio, decidedAt, factId);
		if (updated == 0) {
			return false;
		}
		long passed = decision == DeductionDecision.PASSED ? 1 : 0;
		long deducted = decision == DeductionDecision.DEDUCTED ? 1 : 0;
		int projected = jdbcTemplate.update("""
				INSERT INTO scx_ocpx_hourly_metric (
				  time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,
				  callback_count,deduction_passed_count,deduction_deducted_count
				)
				SELECT CAST(DATE_FORMAT(received_at,'%Y%m%d%H') AS UNSIGNED),advertiser_id,
				       promotion_link_id,media_id,monitor_task_id,1,?,?
				FROM scx_ocpx_event_fact WHERE id=?
				ON DUPLICATE KEY UPDATE
				  deduction_passed_count=deduction_passed_count+VALUES(deduction_passed_count),
				  deduction_deducted_count=deduction_deducted_count+VALUES(deduction_deducted_count),
				  update_time=CURRENT_TIMESTAMP(3)
				""", passed, deducted, factId);
		if (projected == 0) {
			throw new IllegalStateException("media deduction fact could not be projected to hourly metrics");
		}
		return true;
	}

	private CallbackFactResult existingCallback(String factKey) {
		List<CallbackFactResult> facts = jdbcTemplate.query("""
				SELECT id,target_conversion,deduction_decision
				FROM scx_ocpx_event_fact
				WHERE fact_key=? AND flow_type='CALLBACK'
				""", (rs, rowNum) -> new CallbackFactResult(false, rs.getBoolean("target_conversion"),
				rs.getString("id"), rs.getString("deduction_decision")), factKey);
		if (facts.size() != 1) {
			throw new IllegalStateException("duplicate callback fact is unavailable");
		}
		return facts.get(0);
	}

	private boolean insertFact(String factId, String factKey, String flowType, String eventCode, FeedbackReq feedback,
			TaskSnapshot task, boolean targetConversion, BigDecimal spendAmount, BigDecimal currencyAmount,
			String billingStatus, String billingReasonCode, String deductionDecision, LocalDateTime receivedAt) {
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_event_fact (
					  id,fact_key,flow_type,event_code,request_id,advertiser_id,promotion_link_id,
					  media_id,monitor_task_id,target_conversion,billing_enabled_snapshot,billing_type_snapshot,
					  billing_unit_price_snapshot,conversion_event_snapshot,deduction_decision,spend_amount,
					  currency_amount,billing_status,billing_reason_code,received_at
					) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", factId, factKey, flowType, eventCode, feedback.getReqId(), feedback.getAId(),
					feedback.getLId(), feedback.getMId(), feedback.getTId(), targetConversion, task.billingEnabled(),
					task.billingType(), money(task.unitPrice()), task.conversionEvent(), deductionDecision, spendAmount,
					currencyAmount, billingStatus, billingReasonCode, receivedAt);
			return true;
		}
		catch (DuplicateKeyException ignored) {
			return false;
		}
	}

	private TaskSnapshot requireTask(String taskId, String advertiserId, String linkId, String mediaId) {
		List<TaskSnapshot> tasks = jdbcTemplate.query(
				"""
						SELECT id,advertiser_id,promotion_link_id,media_id,billing_enabled,billing_type,unit_price,conversion_event
						FROM scx_ocpx_monitor_task
						WHERE id=? AND advertiser_id=? AND promotion_link_id=? AND media_id=? AND is_delete=0
						""",
				(rs, rowNum) -> new TaskSnapshot(rs.getString("id"), rs.getString("advertiser_id"),
						rs.getString("promotion_link_id"), rs.getString("media_id"), rs.getBoolean("billing_enabled"),
						rs.getInt("billing_type"), rs.getBigDecimal("unit_price"), rs.getString("conversion_event")),
				taskId, advertiserId, linkId, mediaId);
		if (tasks.size() != 1) {
			throw new IllegalArgumentException("monitor task does not match advertiser, promotion link and media");
		}
		return tasks.get(0);
	}

	private BigDecimal lockBalance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT available_balance FROM scx_ocpx_advertiser_account
				WHERE advertiser_id=? FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		if (balances.size() != 1) {
			throw new IllegalStateException("advertiser account is missing");
		}
		return money(balances.get(0));
	}

	private void insertChargeEntry(String entryType, String businessPrefix, String factId, TaskSnapshot task,
			BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String status, String reasonCode,
			LocalDateTime occurredAt) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_billing_entry (
				  id,advertiser_id,monitor_task_id,event_fact_id,entry_type,status,amount,
				  balance_before,balance_after,business_key,reason_code,occurred_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
				""", nextId(), task.advertiserId(), task.id(), factId, entryType, status, amount, balanceBefore,
				balanceAfter, businessPrefix + ":" + factId, reasonCode, occurredAt);
	}

	private void updateFactBilling(String factId, BigDecimal spendAmount, String billingStatus, String reasonCode) {
		jdbcTemplate.update(
				"UPDATE scx_ocpx_event_fact SET spend_amount=?,billing_status=?,billing_reason_code=? WHERE id=?",
				spendAmount, billingStatus, reasonCode, factId);
	}

	private void upsertMetric(LocalDateTime receivedAt, TaskSnapshot task, MetricDelta delta) {
		jdbcTemplate.update(HOURLY_UPSERT, Integer.parseInt(HOUR_KEY.format(receivedAt)), task.advertiserId(),
				task.promotionLinkId(), task.mediaId(), task.id(), delta.impression(), delta.click(), delta.target(),
				delta.callback(), delta.activate(), delta.evoke(), delta.login(), delta.register(), delta.payment(),
				delta.customerAcquisition(), delta.nextStay(), delta.visits(), delta.authentication(),
				delta.placeOrder(), delta.finishTheOrder(), delta.firstActivation(), delta.firstOrder(), delta.spend(),
				delta.currency(), delta.rejectedClick(), delta.rejectedConversion());
	}

	private String feedbackReason(EventTypeEnum event, TaskSnapshot task) {
		if (event != EventTypeEnum.CLICK) {
			return "NON_BILLABLE_EVENT";
		}
		if (!task.billingEnabled()) {
			return "BILLING_DISABLED";
		}
		return task.billingType() == 1 ? null : "BILLING_TYPE_MISMATCH";
	}

	private String callbackReason(boolean targetConversion, TaskSnapshot task) {
		if (!targetConversion) {
			return "NON_TARGET_CONVERSION";
		}
		if (!task.billingEnabled()) {
			return "BILLING_DISABLED";
		}
		return task.billingType() == 2 ? null : "BILLING_TYPE_MISMATCH";
	}

	private static String factKey(FeedbackReq request, String eventCode) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(String.join("|", request.getAId(), request.getTId(), request.getReqId(), eventCode)
					.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String nextId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static void requireFeedback(FeedbackReq request) {
		if (request == null || request.getEvent() == null) {
			throw new IllegalArgumentException("feedback request and event are required");
		}
		requireDimensions(request);
	}

	private static void requireDimensions(FeedbackReq request) {
		if (!StringUtils.hasText(request.getReqId()) || !StringUtils.hasText(request.getAId())
				|| !StringUtils.hasText(request.getTId()) || !StringUtils.hasText(request.getLId())
				|| !StringUtils.hasText(request.getMId())) {
			throw new IllegalArgumentException("requestId and four business dimensions are required");
		}
	}

	private static BigDecimal money(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
	}

	public record FeedbackFactResult(boolean firstEvent, boolean forwardAllowed, String billingStatus,
			BigDecimal spendAmount) {

		private static FeedbackFactResult duplicate() {
			return new FeedbackFactResult(false, false, "DUPLICATE", ZERO);
		}
	}

	public record CallbackFactResult(boolean firstEvent, boolean targetConversion, String factId,
			String deductionDecision) {

		public boolean pendingDeduction() {
			return "PENDING".equals(deductionDecision);
		}
	}

	private record TaskSnapshot(String id, String advertiserId, String promotionLinkId, String mediaId,
			boolean billingEnabled, int billingType, BigDecimal unitPrice, String conversionEvent) {
	}

	private record MetricDelta(long impression, long click, long target, long callback, long activate, long evoke,
			long login, long register, long payment, long customerAcquisition, long nextStay, long visits,
			long authentication, long placeOrder, long finishTheOrder, long firstActivation, long firstOrder,
			BigDecimal spend, BigDecimal currency, long rejectedClick, long rejectedConversion) {

		private static MetricDelta forImpression() {
			return empty(1, 0, 0, ZERO, ZERO, 0, null);
		}

		private static MetricDelta postedClick(BigDecimal spend) {
			return empty(0, 1, 0, spend, ZERO, 0, null);
		}

		private static MetricDelta forClick() {
			return empty(0, 1, 0, ZERO, ZERO, 0, null);
		}

		private static MetricDelta forRejectedClick() {
			return empty(0, 1, 0, ZERO, ZERO, 1, null);
		}

		private static MetricDelta conversion(ActionTypeEnum action, boolean target, BigDecimal currency,
				BigDecimal spend, boolean rejected) {
			MetricDelta base = empty(0, 0, target ? 1 : 0, spend, currency, 0, action);
			return new MetricDelta(base.impression(), base.click(), base.target(), 1, base.activate(), base.evoke(),
					base.login(), base.register(), base.payment(), base.customerAcquisition(), base.nextStay(),
					base.visits(), base.authentication(), base.placeOrder(), base.finishTheOrder(),
					base.firstActivation(), base.firstOrder(), base.spend(), base.currency(), base.rejectedClick(),
					rejected ? 1 : 0);
		}

		private static MetricDelta empty(long impression, long click, long target, BigDecimal spend,
				BigDecimal currency, long rejectedClick, ActionTypeEnum action) {
			return new MetricDelta(impression, click, target, 0, action == ActionTypeEnum.ACTIVATE ? 1 : 0,
					action == ActionTypeEnum.EVOKE ? 1 : 0, action == ActionTypeEnum.LOGIN ? 1 : 0,
					action == ActionTypeEnum.REGISTER ? 1 : 0, action == ActionTypeEnum.PAYMENT ? 1 : 0,
					action == ActionTypeEnum.CUSTOMER_ACQUISITION ? 1 : 0, action == ActionTypeEnum.NEXT_STAY ? 1 : 0,
					action == ActionTypeEnum.VISITS ? 1 : 0, action == ActionTypeEnum.AUTHENTICATION ? 1 : 0,
					action == ActionTypeEnum.ORDER ? 1 : 0, action == ActionTypeEnum.FINISH_THE_ORDER ? 1 : 0,
					action == ActionTypeEnum.FIRST_ACTIVATION ? 1 : 0, action == ActionTypeEnum.FIRST_ORDER ? 1 : 0,
					spend, currency, rejectedClick, 0);
		}
	}

}
