package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.CatalogViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * 广告主资金账户应用服务。
 *
 * <p>
 * 充值与点击扣费共用 {@code scx_ocpx_billing_entry} 作为不可变账务流水。充值先锁定广告主余额行，
 * 再检查业务请求号并在同一数据库事务内写余额和流水，从而保证客户端重试不会重复入账。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AdxAccountService {

	private final JdbcTemplate jdbcTemplate;

	private final AdxAccessScope accessScope;

	@Transactional
	public CatalogViews.AccountMutation recharge(String advertiserId, String requestId, BigDecimal amount,
			String operatorId) {
		String scopedAdvertiserId = requireScope(advertiserId);
		BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
		String chargeEventId = "RECHARGE:" + scopedAdvertiserId + ":" + requestId;
		BigDecimal balanceBefore = lockBalance(scopedAdvertiserId);
		List<CatalogViews.AccountMutation> existing = jdbcTemplate.query("""
				SELECT id,advertiser_id,balance_after FROM scx_ocpx_billing_entry WHERE charge_event_id=?
				""", (rs, rowNum) -> new CatalogViews.AccountMutation(rs.getString(2), rs.getString(1),
				rs.getBigDecimal(3), true), chargeEventId);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		BigDecimal balanceAfter = balanceBefore.add(normalizedAmount);
		String ledgerId = id();
		jdbcTemplate.update("""
				UPDATE scx_adx_advertiser SET balance=?,update_time=NOW(3)
				WHERE id=? AND deleted=0
				""", balanceAfter, scopedAdvertiserId);
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_billing_entry
				(id,charge_event_id,advertiser_id,amount,currency,mode,status,reason,balance_before,balance_after,
				 operator_id,remark,create_time)
				VALUES (?,?,?,?,?,'RECHARGE','POSTED','MANUAL_RECHARGE',?,?,?,?,NOW(3))
				""", ledgerId, chargeEventId, scopedAdvertiserId, normalizedAmount, "CNY", balanceBefore, balanceAfter,
				normalizeOperator(operatorId), "后台余额充值");
		return new CatalogViews.AccountMutation(scopedAdvertiserId, ledgerId, balanceAfter, false);
	}

	public Page<CatalogViews.AccountEntry> ledger(Page<CatalogViews.AccountEntry> page, String advertiserId) {
		String scopedAdvertiserId = requireScope(advertiserId);
		long current = Math.max(1, page.getCurrent());
		long size = Math.min(100, Math.max(1, page.getSize()));
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE advertiser_id=?",
				Long.class, scopedAdvertiserId);
		List<CatalogViews.AccountEntry> records = jdbcTemplate.query(
				"""
						SELECT id,advertiser_id,charge_event_id,channel_id,app_link_id,monitor_id,source_event_code,
						       conversion_event_code,media_event_code,amount,currency,mode,status,reason,balance_before,
						       balance_after,operator_id,remark,create_time
						FROM scx_ocpx_billing_entry WHERE advertiser_id=? ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?
						""",
				(rs, rowNum) -> new CatalogViews.AccountEntry(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getBigDecimal(10), rs.getString(11), rs.getString(12), rs.getString(13),
						rs.getString(14), rs.getBigDecimal(15), rs.getBigDecimal(16), rs.getString(17), rs.getString(18),
						timestamp(rs.getTimestamp(19))),
				scopedAdvertiserId, size, (current - 1) * size);
		Page<CatalogViews.AccountEntry> result = new Page<>(current, size, total == null ? 0 : total);
		result.setRecords(records);
		return result;
	}

	private String requireScope(String requestedAdvertiserId) {
		String scopedAdvertiserId = accessScope.constrainAdvertiser(requestedAdvertiserId);
		if (!StringUtils.hasText(scopedAdvertiserId)) {
			throw new IllegalArgumentException("广告主不能为空");
		}
		return scopedAdvertiserId;
	}

	private BigDecimal lockBalance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT balance FROM scx_adx_advertiser WHERE id=? AND deleted=0 FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		if (balances.isEmpty()) {
			throw new IllegalArgumentException("广告主不存在");
		}
		return balances.get(0);
	}

	private String normalizeOperator(String operatorId) {
		String value = StringUtils.hasText(operatorId) ? operatorId : "SYSTEM";
		return value.length() <= 100 ? value : value.substring(0, 100);
	}

	private java.time.LocalDateTime timestamp(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
