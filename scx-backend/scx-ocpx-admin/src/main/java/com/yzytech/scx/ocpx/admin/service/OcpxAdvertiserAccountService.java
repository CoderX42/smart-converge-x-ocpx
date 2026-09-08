package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;

import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserAccountSqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authoritative MySQL advertiser account and immutable billing ledger. */
@Service
public class OcpxAdvertiserAccountService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final OcpxAdvertiserAccountSqlMapper sqlMapper;

	private final AdvertiserScope advertiserScope;

	public OcpxAdvertiserAccountService(OcpxAdvertiserAccountSqlMapper sqlMapper, AdvertiserScope advertiserScope) {
		this.sqlMapper = sqlMapper;
		this.advertiserScope = advertiserScope;
	}

	public void create(String advertiserId) {
		if (sqlMapper.insertCreateAdvertiserAccount(advertiserId) != 1) {
			throw new IllegalStateException("广告主资金账户创建失败");
		}
	}

	public BigDecimal balance(String advertiserId) {
		List<BigDecimal> balances = sqlMapper.selectBalanceAdvertiserAccount(advertiserId)
			.stream()
			.map(rs -> rs.availableBalance())
			.toList();
		if (balances.size() != 1) {
			throw new IllegalStateException("广告主资金账户不存在");
		}
		return money(balances.get(0));
	}

	public Map<String, BigDecimal> balancesByIds(List<String> advertiserIds) {
		if (advertiserIds == null || advertiserIds.isEmpty()) {
			return Map.of();
		}
		Map<String, BigDecimal> balances = new HashMap<>();
		List<BalanceRow> rows = sqlMapper.selectBalancesByIds(advertiserIds)
			.stream()
			.map(rs -> new BalanceRow(rs.advertiserId(), rs.availableBalance()))
			.toList();
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
		sqlMapper.updateRechargeAdvertiserAccount(balanceAfter, advertiserId);
		String entryId = nextId();
		LocalDateTime occurredAt = LocalDateTime.now(BUSINESS_ZONE);
		BootUser operator = SecurityUtils.getUser();
		sqlMapper.insertRechargeBillingEntry(new OcpxAdvertiserAccountSqlMapper.InsertRechargeBillingEntryCommand(
				entryId, advertiserId, amount, balanceBefore, balanceAfter, businessKey,
				operator == null ? null : operator.getUserId(), trim(request.getRemark()), occurredAt));
		return new AdvertiserViews.RechargeResult(entryId, advertiserId, amount, balanceAfter, occurredAt);
	}

	@Transactional
	public AdvertiserViews.DeductionResult deduct(String advertiserId, AdvertiserRequests.Deduction request) {
		advertiserScope.requireSystemAdministrator();
		String requestId = request.getRequestId().trim();
		BigDecimal amount = money(request.getAmount());
		String remark = request.getRemark().trim();
		String businessKey = "MANUAL_DEBIT:" + advertiserId + ":" + requestId;
		BigDecimal balanceBefore = lockBalance(advertiserId);
		AdvertiserViews.DeductionResult existing = findDeduction(businessKey, advertiserId, amount, remark);
		if (existing != null) {
			return existing;
		}
		if (balanceBefore.compareTo(amount) < 0) {
			throw new BootBusinessException("扣减金额不能超过当前可用余额");
		}

		BigDecimal balanceAfter = balanceBefore.subtract(amount).setScale(6, RoundingMode.UNNECESSARY);
		sqlMapper.updateDeductAdvertiserAccount(balanceAfter, advertiserId);
		String entryId = nextId();
		LocalDateTime occurredAt = LocalDateTime.now(BUSINESS_ZONE);
		BootUser operator = SecurityUtils.getUser();
		sqlMapper.insertDeductBillingEntry(new OcpxAdvertiserAccountSqlMapper.InsertDeductBillingEntryCommand(entryId,
				advertiserId, amount, balanceBefore, balanceAfter, businessKey,
				operator == null ? null : operator.getUserId(), remark, occurredAt));
		return new AdvertiserViews.DeductionResult(entryId, advertiserId, amount, balanceAfter, occurredAt);
	}

	public void assertDeletable(String advertiserId) {
		BigDecimal balance = balance(advertiserId);
		Long facts = sqlMapper.selectAssertDeletableEventFact(advertiserId);
		Long ledger = sqlMapper.selectAssertDeletableBillingEntry(advertiserId);
		Long billingAudits = sqlMapper.selectAssertDeletableBillingConfigAudit(advertiserId);
		if (balance.signum() != 0 || facts == null || facts.longValue() != 0 || ledger == null
				|| ledger.longValue() != 0 || billingAudits == null || billingAudits.longValue() != 0) {
			throw new BootBusinessException("广告主已有余额、账务、事件事实或计费配置审计，禁止物理删除");
		}
	}

	public void deleteEmptyAccount(String advertiserId) {
		if (sqlMapper.deleteDeleteEmptyAccountAdvertiserAccount(advertiserId) != 1) {
			throw new IllegalStateException("广告主资金账户删除失败");
		}
	}

	private BigDecimal lockBalance(String advertiserId) {
		List<BigDecimal> balances = sqlMapper.selectLockBalanceAdvertiserAccount(advertiserId)
			.stream()
			.map(rs -> rs.availableBalance())
			.toList();
		if (balances.size() != 1) {
			throw new BootBusinessException("广告主资金账户不存在");
		}
		return money(balances.get(0));
	}

	private AdvertiserViews.RechargeResult findRecharge(String businessKey, String advertiserId, BigDecimal amount) {
		List<AdvertiserViews.RechargeResult> entries = sqlMapper.selectFindRechargeBillingEntry(businessKey)
			.stream()
			.map(rs -> new AdvertiserViews.RechargeResult(rs.id(), rs.advertiserId(), rs.amount(), rs.balanceAfter(),
					rs.occurredAt().toLocalDateTime()))
			.toList();
		if (entries.isEmpty()) {
			return null;
		}
		AdvertiserViews.RechargeResult existing = entries.get(0);
		if (!advertiserId.equals(existing.getAdvertiserId()) || existing.getAmount().compareTo(amount) != 0) {
			throw new BootBusinessException("同一 requestId 不允许变更充值对象或金额");
		}
		return existing;
	}

	private AdvertiserViews.DeductionResult findDeduction(String businessKey, String advertiserId, BigDecimal amount,
			String remark) {
		List<DeductionEntry> entries = sqlMapper.selectFindDeductionBillingEntry(businessKey)
			.stream()
			.map(rs -> new DeductionEntry(new AdvertiserViews.DeductionResult(rs.id(), rs.advertiserId(), rs.amount(),
					rs.balanceAfter(), rs.occurredAt().toLocalDateTime()), rs.remark()))
			.toList();
		if (entries.isEmpty()) {
			return null;
		}
		DeductionEntry entry = entries.get(0);
		AdvertiserViews.DeductionResult existing = entry.result();
		if (!advertiserId.equals(existing.getAdvertiserId()) || existing.getAmount().compareTo(amount) != 0
				|| !remark.equals(entry.remark())) {
			throw new BootBusinessException("同一 requestId 不允许变更扣减对象、金额或原因");
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

	private record DeductionEntry(AdvertiserViews.DeductionResult result, String remark) {
	}

}
