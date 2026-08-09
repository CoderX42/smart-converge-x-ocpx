package com.yzytech.scx.ocpx.runtime.billing;

import com.yzytech.scx.ocpx.contract.BillingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 MySQL 行锁和唯一事件号的 CPC / CPA 计费实现。
 *
 * <p>
 * 事件、余额变更和账务流水参加调用方的同一事务。同一业务事件的重放只返回已有流水；余额不足时记录
 * REJECTED 流水但不扣余额，CPA 允许充值后用同一事件再次校验。
 * </p>
 */
@Component
public class JdbcBillingPort implements BillingPort {

	private static final Set<String> SUPPORTED_MODES = Set.of("AUDIT_ONLY", "CPC", "CPA");

	private final JdbcTemplate jdbcTemplate;

	public JdbcBillingPort(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency,
			String mode) {
		return charge(chargeEventId, advertiserId, amount, currency, mode, null);
	}

	@Override
	@Transactional
	public BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency,
			String mode, String debugExecutionId) {
		if (!SUPPORTED_MODES.contains(mode)) {
			return BillingResult.disabled();
		}
		if (!"CNY".equals(currency)) {
			throw new IllegalArgumentException("only CNY click billing is supported");
		}
		if (amount == null || amount.signum() < 0 || amount.scale() > 6
				|| (amount.signum() == 0 && !"CPA".equals(mode))) {
			throw new IllegalArgumentException("billing amount must be non-negative with at most 6 decimals");
		}
		BigDecimal balanceBefore = lockBalance(advertiserId);
		List<BillingResult> existing = jdbcTemplate.query("""
				SELECT status,reason,balance_after FROM scx_ocpx_billing_entry
				WHERE charge_event_id=? AND advertiser_id=? AND mode=? AND amount=? AND currency=?
				""", (rs, rowNum) -> new BillingResult(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)),
				chargeEventId, advertiserId, mode, amount, currency);
		if (!existing.isEmpty() && !("CPA".equals(mode) && "REJECTED".equals(existing.get(0).status()))) {
			return existing.get(0);
		}
		if ("AUDIT_ONLY".equals(mode)) {
			insertEntry(chargeEventId, advertiserId, amount, currency, mode, "RECORDED", "NO_BALANCE_MUTATION",
					balanceBefore, balanceBefore, debugExecutionId);
			return new BillingResult("RECORDED", "NO_BALANCE_MUTATION", balanceBefore);
		}
		if ("CPA".equals(mode) && amount.signum() == 0) {
			if (existing.isEmpty()) {
				insertEntry(chargeEventId, advertiserId, amount, currency, mode, "POSTED", "FREE_EVENT", balanceBefore,
						balanceBefore, debugExecutionId);
			}
			else {
				updateRejectedEntry(chargeEventId, "POSTED", "FREE_EVENT", balanceBefore, balanceBefore);
			}
			return new BillingResult("POSTED", "FREE_EVENT", balanceBefore);
		}
		if (balanceBefore.compareTo(amount) < 0) {
			if (existing.isEmpty()) {
				insertEntry(chargeEventId, advertiserId, amount, currency, mode, "REJECTED", "INSUFFICIENT_BALANCE",
						balanceBefore, balanceBefore, debugExecutionId);
			}
			else {
				updateRejectedEntry(chargeEventId, "REJECTED", "INSUFFICIENT_BALANCE", balanceBefore, balanceBefore);
			}
			return new BillingResult("REJECTED", "INSUFFICIENT_BALANCE", balanceBefore);
		}
		BigDecimal balanceAfter = balanceBefore.subtract(amount);
		int updated = jdbcTemplate.update("""
				UPDATE scx_adx_advertiser SET balance=?,update_time=NOW(3) WHERE id=? AND deleted=0
				""", balanceAfter, advertiserId);
		if (updated != 1) {
			throw new IllegalStateException("advertiser balance update failed");
		}
		String reason = "CPA".equals(mode) ? "CONVERSION_CHARGED" : "CLICK_CHARGED";
		if (existing.isEmpty()) {
			insertEntry(chargeEventId, advertiserId, amount, currency, mode, "POSTED", reason, balanceBefore,
					balanceAfter, debugExecutionId);
		}
		else {
			updateRejectedEntry(chargeEventId, "POSTED", reason, balanceBefore, balanceAfter);
		}
		return new BillingResult("POSTED", reason, balanceAfter);
	}

	private BigDecimal lockBalance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT balance FROM scx_adx_advertiser WHERE id=? AND deleted=0 FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		if (balances.isEmpty()) {
			throw new IllegalArgumentException("advertiser does not exist");
		}
		return balances.get(0);
	}

	private void insertEntry(String chargeEventId, String advertiserId, BigDecimal amount, String currency, String mode,
			String status, String reason, BigDecimal balanceBefore, BigDecimal balanceAfter, String debugExecutionId) {
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_billing_entry
						(id,charge_event_id,debug_execution_id,source,advertiser_id,amount,currency,mode,status,reason,balance_before,balance_after,
						 operator_id,remark,create_time)
						VALUES (?,?,?, ?,?,?,?,?,?,?,?,?,'SYSTEM',?,NOW(3))
						""",
				id(), chargeEventId, debugExecutionId, debugExecutionId == null ? "BUSINESS" : "DEBUG", advertiserId,
				amount, currency, mode, status, reason, balanceBefore, balanceAfter,
				"CPA".equals(mode) ? "OCPX CPA" : "OCPX CLICK");
	}

	private void updateRejectedEntry(String chargeEventId, String status, String reason, BigDecimal balanceBefore,
			BigDecimal balanceAfter) {
		jdbcTemplate.update("""
				UPDATE scx_ocpx_billing_entry
				SET status=?,reason=?,balance_before=?,balance_after=?,remark='OCPX CPA',create_time=NOW(3)
				WHERE charge_event_id=? AND mode='CPA' AND status='REJECTED'
				""", status, reason, balanceBefore, balanceAfter, chargeEventId);
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
