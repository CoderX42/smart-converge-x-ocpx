package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authoritative MySQL advertiser account and immutable billing ledger. */
@Service
public class OcpxAdvertiserAccountService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	public OcpxAdvertiserAccountService(JdbcTemplate jdbcTemplate, AdvertiserScope advertiserScope) {
		this.jdbcTemplate = jdbcTemplate;
		this.advertiserScope = advertiserScope;
	}

	public void create(String advertiserId) {
		if (jdbcTemplate.update("""
				INSERT INTO scx_ocpx_advertiser_account (advertiser_id,currency,available_balance)
				VALUES (?,'CNY',0)
				""", advertiserId) != 1) {
			throw new IllegalStateException("广告主资金账户创建失败");
		}
	}

	public BigDecimal balance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT available_balance FROM scx_ocpx_advertiser_account WHERE advertiser_id=?
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		if (balances.size() != 1) {
			throw new IllegalStateException("广告主资金账户不存在");
		}
		return money(balances.get(0));
	}

	public Map<String, BigDecimal> balancesByIds(List<String> advertiserIds) {
		if (advertiserIds == null || advertiserIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", Collections.nCopies(advertiserIds.size(), "?"));
		Map<String, BigDecimal> balances = new HashMap<>();
		List<BalanceRow> rows = jdbcTemplate.query(
				"SELECT advertiser_id,available_balance FROM scx_ocpx_advertiser_account WHERE advertiser_id IN ("
						+ placeholders + ")",
				(rs, rowNum) -> new BalanceRow(rs.getString(1), rs.getBigDecimal(2)), advertiserIds.toArray());
		rows.forEach(row -> balances.put(row.advertiserId(), money(row.balance())));
		advertiserIds.forEach(id -> balances.putIfAbsent(id, BigDecimal.ZERO.setScale(6)));
		return balances;
	}

	@Transactional
	public AdvertiserViews.RechargeResult recharge(String advertiserId, AdvertiserRequests.Recharge request) {
		advertiserScope.requireSystemAdministrator();
		String requestId = request.getRequestId().trim();
		BigDecimal amount = money(request.getAmount());
		String businessKey = "RECHARGE:" + advertiserId + ":" + requestId;
		BigDecimal balanceBefore = lockBalance(advertiserId);
		AdvertiserViews.RechargeResult existing = findRecharge(businessKey, advertiserId, amount);
		if (existing != null) {
			return existing;
		}

		BigDecimal balanceAfter = balanceBefore.add(amount).setScale(6, RoundingMode.UNNECESSARY);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_advertiser_account
				SET available_balance=?,update_time=CURRENT_TIMESTAMP(3)
				WHERE advertiser_id=?
				""", balanceAfter, advertiserId);
		String entryId = nextId();
		LocalDateTime occurredAt = LocalDateTime.now(BUSINESS_ZONE);
		BootUser operator = SecurityUtils.getUser();
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_billing_entry (
				  id,advertiser_id,entry_type,status,amount,balance_before,balance_after,
				  business_key,operator_user_id,remark,occurred_at
				) VALUES (?,?,'RECHARGE','POSTED',?,?,?,?,?,?,?)
				""", entryId, advertiserId, amount, balanceBefore, balanceAfter, businessKey,
				operator == null ? null : operator.getUserId(), trim(request.getRemark()), occurredAt);
		return new AdvertiserViews.RechargeResult(entryId, advertiserId, amount, balanceAfter, occurredAt);
	}

	public void assertDeletable(String advertiserId) {
		BigDecimal balance = balance(advertiserId);
		Long facts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_event_fact WHERE advertiser_id=?",
				Long.class, advertiserId);
		Long ledger = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=?",
				Long.class, advertiserId);
		if (balance.signum() != 0 || facts == null || facts.longValue() != 0 || ledger == null
				|| ledger.longValue() != 0) {
			throw new BootBusinessException("广告主已有余额、账务或事件事实，禁止物理删除");
		}
	}

	public void deleteEmptyAccount(String advertiserId) {
		if (jdbcTemplate.update("DELETE FROM scx_ocpx_advertiser_account WHERE advertiser_id=?", advertiserId) != 1) {
			throw new IllegalStateException("广告主资金账户删除失败");
		}
	}

	private BigDecimal lockBalance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT available_balance FROM scx_ocpx_advertiser_account
				WHERE advertiser_id=? FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		if (balances.size() != 1) {
			throw new BootBusinessException("广告主资金账户不存在");
		}
		return money(balances.get(0));
	}

	private AdvertiserViews.RechargeResult findRecharge(String businessKey, String advertiserId, BigDecimal amount) {
		List<AdvertiserViews.RechargeResult> entries = jdbcTemplate.query("""
				SELECT id,advertiser_id,amount,balance_after,occurred_at
				FROM scx_ocpx_billing_entry WHERE business_key=?
				""", (rs, rowNum) -> new AdvertiserViews.RechargeResult(rs.getString("id"),
					rs.getString("advertiser_id"), rs.getBigDecimal("amount"), rs.getBigDecimal("balance_after"),
					rs.getTimestamp("occurred_at").toLocalDateTime()), businessKey);
		if (entries.isEmpty()) {
			return null;
		}
		AdvertiserViews.RechargeResult existing = entries.get(0);
		if (!advertiserId.equals(existing.getAdvertiserId()) || existing.getAmount().compareTo(amount) != 0) {
			throw new BootBusinessException("同一 requestId 不允许变更充值对象或金额");
		}
		return existing;
	}

	private static BigDecimal money(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
	}

	private static String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static String nextId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record BalanceRow(String advertiserId, BigDecimal balance) {
	}

}
