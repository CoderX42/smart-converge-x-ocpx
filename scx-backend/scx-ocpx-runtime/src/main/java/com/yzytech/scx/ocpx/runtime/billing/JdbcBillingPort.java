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
 * 基于 MySQL 行锁和唯一事件号的点击计费实现。
 *
 * <p>
 * 事件、余额变更和账务流水参加调用方的同一事务。对同一点击事件的重放只返回已有流水；余额不足时 记录 REJECTED 流水但不扣余额，调用方据此禁止继续向上游投递。
 * </p>
 */
@Component
public class JdbcBillingPort implements BillingPort {

	private static final Set<String> SUPPORTED_MODES = Set.of("AUDIT_ONLY", "CPC");

	private final JdbcTemplate jdbcTemplate;

	public JdbcBillingPort(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public BillingResult charge(String chargeEventId, String advertiserId, BigDecimal amount, String currency,
			String mode) {
		if (!SUPPORTED_MODES.contains(mode)) {
			return BillingResult.disabled();
		}
		if (!"CNY".equals(currency)) {
			throw new IllegalArgumentException("only CNY click billing is supported");
		}
		if (amount == null || amount.signum() <= 0 || amount.scale() > 6) {
			throw new IllegalArgumentException("click billing amount must be positive with at most 6 decimals");
		}
		BigDecimal balanceBefore = lockBalance(advertiserId);
		List<BillingResult> existing = jdbcTemplate.query("""
				SELECT status,reason,balance_after FROM scx_ocpx_billing_entry WHERE charge_event_id=?
				""", (rs, rowNum) -> new BillingResult(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)),
				chargeEventId);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		if ("AUDIT_ONLY".equals(mode)) {
			insertEntry(chargeEventId, advertiserId, amount, currency, mode, "RECORDED", "NO_BALANCE_MUTATION",
					balanceBefore, balanceBefore);
			return new BillingResult("RECORDED", "NO_BALANCE_MUTATION", balanceBefore);
		}
		if (balanceBefore.compareTo(amount) < 0) {
			insertEntry(chargeEventId, advertiserId, amount, currency, mode, "REJECTED", "INSUFFICIENT_BALANCE",
					balanceBefore, balanceBefore);
			return new BillingResult("REJECTED", "INSUFFICIENT_BALANCE", balanceBefore);
		}
		BigDecimal balanceAfter = balanceBefore.subtract(amount);
		int updated = jdbcTemplate.update("""
				UPDATE scx_adx_advertiser SET balance=?,update_time=NOW(3) WHERE id=? AND deleted=0
				""", balanceAfter, advertiserId);
		if (updated != 1) {
			throw new IllegalStateException("advertiser balance update failed");
		}
		insertEntry(chargeEventId, advertiserId, amount, currency, mode, "POSTED", "CLICK_CHARGED", balanceBefore,
				balanceAfter);
		return new BillingResult("POSTED", "CLICK_CHARGED", balanceAfter);
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
			String status, String reason, BigDecimal balanceBefore, BigDecimal balanceAfter) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_billing_entry
				(id,charge_event_id,advertiser_id,amount,currency,mode,status,reason,balance_before,balance_after,
				 operator_id,remark,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,?,'SYSTEM','OCPX CLICK',NOW(3))
				""", id(), chargeEventId, advertiserId, amount, currency, mode, status, reason, balanceBefore,
				balanceAfter);
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
