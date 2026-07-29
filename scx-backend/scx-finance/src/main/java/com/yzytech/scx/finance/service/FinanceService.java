package com.yzytech.scx.finance.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.commercial.port.CommercialAccountPort;
import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.finance.entity.*;
import com.yzytech.scx.finance.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core double-entry ledger service.
 * <p>
 * Every monetary movement produces a balanced journal (sum debit == sum credit).
 * Idempotency via (businessType, businessId). Concurrent writes protected by optimistic
 * locking (version column).
 * <p>
 * Available funds are held directly in CASH and CREDIT. A reservation transfers funds
 * into RESERVATION, so it must not be subtracted for a second time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceService {

	private static final Set<String> ACCOUNT_TYPES = Set.of("CASH", "CREDIT", "RESERVATION", "ESTIMATED_SPEND",
			"CONFIRMED_SPEND", "REBATE", "REFUND_PAYABLE", "INVOICEABLE");

	private final FinAccountMapper accountMapper;

	private final FinJournalMapper journalMapper;

	private final FinEntryMapper entryMapper;

	private final CreditLineMapper creditLineMapper;

	private final BudgetReservationMapper reservationMapper;

	private final OrganizationScopeService scopeService;

	private final CommercialAccountPort commercialAccountPort;

	// ── Account ──

	public FinAccount requireAccount(String id) {
		FinAccount a = accountMapper.selectById(id);
		if (a == null || !scopeService.canRead(a.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return a;
	}

	public FinAccount getOrCreateAccount(String orgId, String accountType) {
		scopeService.requireWrite(orgId);
		if (!ACCOUNT_TYPES.contains(accountType)) {
			throw new IllegalArgumentException("无效财务账户类型");
		}
		FinAccount existing = accountMapper.selectOne(Wrappers.<FinAccount>lambdaQuery()
			.eq(FinAccount::getOrgId, orgId)
			.eq(FinAccount::getAccountType, accountType));
		if (existing != null)
			return existing;
		FinAccount a = new FinAccount();
		a.setOrgId(orgId);
		a.setAccountType(accountType);
		a.setBalance(BigDecimal.ZERO);
		a.setCurrency("CNY");
		a.setStatus("ACTIVE");
		a.setVersion(0);
		accountMapper.insert(a);
		return a;
	}

	public java.util.List<FinAccount> listAccounts(String orgId) {
		scopeService.requireRead(orgId);
		return accountMapper.selectList(Wrappers.<FinAccount>lambdaQuery().eq(FinAccount::getOrgId, orgId));
	}

	public Page<FinAccount> pageAccounts(Page<FinAccount> page, String orgId, String accountType) {
		scopeService.requireRead(orgId);
		return accountMapper.selectPage(page,
				Wrappers.<FinAccount>lambdaQuery()
					.eq(FinAccount::getOrgId, orgId)
					.eq(accountType != null, FinAccount::getAccountType, accountType)
					.orderByAsc(FinAccount::getAccountType));
	}

	// ── Balance ──

	public BigDecimal getAvailableBalance(String orgId) {
		scopeService.requireRead(orgId);
		BigDecimal cash = getAccountBalance(orgId, "CASH");
		BigDecimal credit = getAvailableCredit(orgId);
		return cash.add(credit);
	}

	public BigDecimal getAccountBalance(String orgId, String accountType) {
		scopeService.requireRead(orgId);
		FinAccount a = accountMapper.selectOne(Wrappers.<FinAccount>lambdaQuery()
			.eq(FinAccount::getOrgId, orgId)
			.eq(FinAccount::getAccountType, accountType));
		return a != null ? a.getBalance() : BigDecimal.ZERO;
	}

	// ── Recharge ──

	@Transactional
	public FinJournal recharge(String orgId, BigDecimal amount, String businessId, String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "充值金额");
		checkIdempotent("RECHARGE", businessId);
		FinAccount cash = getOrCreateAccount(orgId, "CASH");
		FinAccount contra = getOrCreateAccount(orgId, "REFUND_PAYABLE"); // contra for
																			// double-entry
																			// balance
		cash.setBalance(cash.getBalance().add(amount));
		enforceOptimisticUpdate(cash);
		contra.setBalance(contra.getBalance().subtract(amount));
		enforceOptimisticUpdate(contra);
		return post("RECHARGE", "RECHARGE", businessId, orgId, memo,
				e(cash.getId(), "DEBIT", amount, cash.getBalance()),
				e(contra.getId(), "CREDIT", amount, contra.getBalance()));
	}

	// ── Gift ──

	@Transactional
	public FinJournal gift(String orgId, BigDecimal amount, String businessId, String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "赠送金额");
		checkIdempotent("GIFT", businessId);
		FinAccount cash = getOrCreateAccount(orgId, "CASH");
		FinAccount contra = getOrCreateAccount(orgId, "REFUND_PAYABLE");
		cash.setBalance(cash.getBalance().add(amount));
		enforceOptimisticUpdate(cash);
		contra.setBalance(contra.getBalance().subtract(amount));
		enforceOptimisticUpdate(contra);
		return post("GIFT", "GIFT", businessId, orgId, memo, e(cash.getId(), "DEBIT", amount, cash.getBalance()),
				e(contra.getId(), "CREDIT", amount, contra.getBalance()));
	}

	@Transactional
	public FinJournal refundCash(String orgId, BigDecimal amount, String businessId, String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "退款金额");
		checkIdempotent("REFUND", businessId);
		FinAccount cash = getOrCreateAccount(orgId, "CASH");
		if (cash.getBalance().compareTo(amount) < 0) {
			throw new IllegalArgumentException("可退款余额不足");
		}
		FinAccount contra = getOrCreateAccount(orgId, "REFUND_PAYABLE");
		cash.setBalance(cash.getBalance().subtract(amount));
		contra.setBalance(contra.getBalance().add(amount));
		enforceOptimisticUpdate(cash);
		enforceOptimisticUpdate(contra);
		return post("REFUND", "REFUND", businessId, orgId, memo,
				e(contra.getId(), "DEBIT", amount, contra.getBalance()),
				e(cash.getId(), "CREDIT", amount, cash.getBalance()));
	}

	// ── Credit ──

	@Transactional
	public CreditLine grantCredit(String orgId, BigDecimal amount, LocalDateTime expiredAt, String businessId) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "授信额度");
		checkIdempotent("CREDIT_GRANT", businessId);
		if (expiredAt != null && !expiredAt.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("授信失效时间必须晚于当前时间");
		}
		if (creditLineMapper.selectCount(Wrappers.<CreditLine>lambdaQuery()
			.eq(CreditLine::getOrgId, orgId)
			.eq(CreditLine::getStatus, "ACTIVE")) > 0) {
			throw new IllegalArgumentException("组织已有生效授信");
		}
		CreditLine c = new CreditLine();
		c.setOrgId(orgId);
		c.setCreditAmount(amount);
		c.setUsedAmount(BigDecimal.ZERO);
		c.setAvailableAmount(amount);
		c.setCurrency("CNY");
		c.setStatus("ACTIVE");
		c.setGrantedAt(LocalDateTime.now());
		c.setExpiredAt(expiredAt);
		c.setVersion(0);
		creditLineMapper.insert(c);
		FinAccount credit = getOrCreateAccount(orgId, "CREDIT");
		FinAccount contra = getOrCreateAccount(orgId, "REFUND_PAYABLE");
		credit.setBalance(credit.getBalance().add(amount));
		enforceOptimisticUpdate(credit);
		contra.setBalance(contra.getBalance().subtract(amount));
		enforceOptimisticUpdate(contra);
		post("CREDIT_GRANT", "CREDIT_GRANT", businessId, orgId, "授信:" + amount,
				e(credit.getId(), "DEBIT", amount, credit.getBalance()),
				e(contra.getId(), "CREDIT", amount, contra.getBalance()));
		return c;
	}

	public BigDecimal getAvailableCredit(String orgId) {
		return getAccountBalance(orgId, "CREDIT");
	}

	public Page<CreditLine> pageCreditLines(Page<CreditLine> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return creditLineMapper.selectPage(page,
				Wrappers.<CreditLine>lambdaQuery()
					.eq(CreditLine::getOrgId, orgId)
					.eq(status != null, CreditLine::getStatus, status)
					.orderByDesc(CreditLine::getCreateTime));
	}

	// ── Budget Reservation ──

	@Transactional
	public BudgetReservation reserveBudget(String orgId, BigDecimal amount, String adAccountId, String businessId,
			String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "预留金额");
		checkIdempotent("BUDGET_RESERVE", businessId);
		if (adAccountId != null) {
			var account = commercialAccountPort.requireAccount(adAccountId);
			if (!orgId.equals(account.responsibleOrganizationId())) {
				throw new IllegalArgumentException("资源不存在");
			}
		}
		BigDecimal available = getAvailableBalance(orgId);
		if (available.compareTo(amount) < 0)
			throw new IllegalArgumentException("可用余额不足: " + available + " < " + amount);

		FinAccount cash = getOrCreateAccount(orgId, "CASH");
		FinAccount credit = getOrCreateAccount(orgId, "CREDIT");
		FinAccount reserve = getOrCreateAccount(orgId, "RESERVATION");
		BigDecimal cashAllocation = cash.getBalance().min(amount).max(BigDecimal.ZERO);
		BigDecimal creditAllocation = amount.subtract(cashAllocation);
		if (credit.getBalance().compareTo(creditAllocation) < 0) {
			throw new IllegalArgumentException("可用余额不足");
		}
		cash.setBalance(cash.getBalance().subtract(cashAllocation));
		credit.setBalance(credit.getBalance().subtract(creditAllocation));
		reserve.setBalance(reserve.getBalance().add(amount));
		if (cashAllocation.signum() > 0) {
			enforceOptimisticUpdate(cash);
		}
		if (creditAllocation.signum() > 0) {
			enforceOptimisticUpdate(credit);
			adjustCreditUsage(orgId, creditAllocation);
		}
		enforceOptimisticUpdate(reserve);

		BudgetReservation r = new BudgetReservation();
		r.setOrgId(orgId);
		r.setAdAccountId(adAccountId);
		r.setReservedAmount(amount);
		r.setConsumedAmount(BigDecimal.ZERO);
		r.setReleasedAmount(BigDecimal.ZERO);
		r.setCurrency("CNY");
		r.setStatus("ACTIVE");
		r.setCashReservedAmount(cashAllocation);
		r.setCreditReservedAmount(creditAllocation);
		r.setCashConsumedAmount(BigDecimal.ZERO);
		r.setCreditConsumedAmount(BigDecimal.ZERO);
		r.setCashReleasedAmount(BigDecimal.ZERO);
		r.setCreditReleasedAmount(BigDecimal.ZERO);
		r.setBusinessId(businessId);
		r.setReservedAt(LocalDateTime.now());
		r.setVersion(0);
		reservationMapper.insert(r);
		List<FinEntryBuilder> entries = new ArrayList<>();
		entries.add(e(reserve.getId(), "DEBIT", amount, reserve.getBalance()));
		if (cashAllocation.signum() > 0) {
			entries.add(e(cash.getId(), "CREDIT", cashAllocation, cash.getBalance()));
		}
		if (creditAllocation.signum() > 0) {
			entries.add(e(credit.getId(), "CREDIT", creditAllocation, credit.getBalance()));
		}
		FinJournal journal = post("BUDGET_RESERVE", "BUDGET_RESERVE", businessId, orgId, memo,
				entries.toArray(FinEntryBuilder[]::new));
		r.setReserveJournalId(journal.getId());
		reservationMapper.updateById(r);
		return r;
	}

	public BigDecimal getActiveReservations(String orgId) {
		scopeService.requireRead(orgId);
		return reservationMapper
			.selectList(Wrappers.<BudgetReservation>lambdaQuery()
				.eq(BudgetReservation::getOrgId, orgId)
				.eq(BudgetReservation::getStatus, "ACTIVE"))
			.stream()
			.map(r -> r.getReservedAmount().subtract(r.getConsumedAmount()).subtract(r.getReleasedAmount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public List<BudgetReservation> listReservations(String orgId, String status) {
		scopeService.requireRead(orgId);
		return reservationMapper.selectList(Wrappers.<BudgetReservation>lambdaQuery()
			.eq(BudgetReservation::getOrgId, orgId)
			.eq(status != null && !status.isBlank(), BudgetReservation::getStatus, status)
			.orderByDesc(BudgetReservation::getReservedAt));
	}

	public Page<BudgetReservation> pageReservations(Page<BudgetReservation> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return reservationMapper.selectPage(page,
				Wrappers.<BudgetReservation>lambdaQuery()
					.eq(BudgetReservation::getOrgId, orgId)
					.eq(status != null, BudgetReservation::getStatus, status)
					.orderByDesc(BudgetReservation::getCreateTime));
	}

	public BigDecimal getReservationRemaining(String orgId, String reservationId) {
		scopeService.requireRead(orgId);
		BudgetReservation reservation = reservationMapper.selectById(reservationId);
		if (reservation == null || !orgId.equals(reservation.getOrgId()))
			throw new IllegalArgumentException("资源不存在");
		return reservation.getReservedAmount()
			.subtract(defaultZero(reservation.getConsumedAmount()))
			.subtract(defaultZero(reservation.getReleasedAmount()));
	}

	@Transactional
	public FinJournal releaseRemainingReservation(String orgId, String reservationId, String businessId, String memo) {
		BigDecimal remaining = getReservationRemaining(orgId, reservationId);
		if (remaining.signum() == 0)
			return null;
		return releaseReservation(orgId, reservationId, remaining, businessId, memo);
	}

	// ── Estimated Spend ──

	@Transactional
	public FinJournal estimateSpend(String orgId, String reservationId, BigDecimal amount, String businessId,
			String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "消耗金额");
		checkIdempotent("ESTIMATED_SPEND", businessId);
		BudgetReservation r = requireReservation(reservationId);
		requireReservationOrganization(r, orgId);
		BigDecimal remaining = r.getReservedAmount().subtract(r.getConsumedAmount()).subtract(r.getReleasedAmount());
		if (remaining.compareTo(amount) < 0)
			throw new IllegalArgumentException("预留余额不足");
		r.setConsumedAmount(r.getConsumedAmount().add(amount));
		BigDecimal cashRemaining = defaultZero(r.getCashReservedAmount())
			.subtract(defaultZero(r.getCashConsumedAmount()))
			.subtract(defaultZero(r.getCashReleasedAmount()));
		BigDecimal cashConsumption = cashRemaining.min(amount).max(BigDecimal.ZERO);
		BigDecimal creditConsumption = amount.subtract(cashConsumption);
		r.setCashConsumedAmount(defaultZero(r.getCashConsumedAmount()).add(cashConsumption));
		r.setCreditConsumedAmount(defaultZero(r.getCreditConsumedAmount()).add(creditConsumption));
		if (r.getConsumedAmount().add(r.getReleasedAmount()).compareTo(r.getReservedAmount()) >= 0) {
			r.setStatus("CONSUMED");
		}
		enforceOptimisticUpdate(r);

		FinAccount est = getOrCreateAccount(orgId, "ESTIMATED_SPEND");
		est.setBalance(est.getBalance().add(amount));
		enforceOptimisticUpdate(est);

		FinAccount reserve = getOrCreateAccount(orgId, "RESERVATION");
		reserve.setBalance(reserve.getBalance().subtract(amount));
		enforceOptimisticUpdate(reserve);

		return post("ESTIMATED_SPEND", "ESTIMATED_SPEND", businessId, orgId, memo,
				e(est.getId(), "DEBIT", amount, est.getBalance()),
				e(reserve.getId(), "CREDIT", amount, reserve.getBalance()));
	}

	// ── Confirmed Spend ──

	@Transactional
	public FinJournal confirmSpend(String orgId, String reservationId, BigDecimal amount, String businessId,
			String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "确认金额");
		checkIdempotent("CONFIRMED_SPEND", businessId);
		BudgetReservation reservation = requireReservationForConfirmation(reservationId);
		requireReservationOrganization(reservation, orgId);
		FinAccount est = getOrCreateAccount(orgId, "ESTIMATED_SPEND");
		FinAccount confirmed = getOrCreateAccount(orgId, "CONFIRMED_SPEND");
		if (est.getBalance().compareTo(amount) < 0)
			throw new IllegalArgumentException("预估消耗余额不足");
		est.setBalance(est.getBalance().subtract(amount));
		enforceOptimisticUpdate(est);
		confirmed.setBalance(confirmed.getBalance().add(amount));
		enforceOptimisticUpdate(confirmed);
		return post("CONFIRMED_SPEND", "CONFIRMED_SPEND", businessId, orgId, memo,
				e(confirmed.getId(), "DEBIT", amount, confirmed.getBalance()),
				e(est.getId(), "CREDIT", amount, est.getBalance()));
	}

	// ── Release ──

	@Transactional
	public FinJournal releaseReservation(String orgId, String reservationId, BigDecimal amount, String businessId,
			String memo) {
		scopeService.requireWrite(orgId);
		checkPositive(amount, "释放金额");
		checkIdempotent("BUDGET_RELEASE", businessId);
		BudgetReservation r = requireReservation(reservationId);
		requireReservationOrganization(r, orgId);
		BigDecimal remaining = r.getReservedAmount().subtract(r.getConsumedAmount()).subtract(r.getReleasedAmount());
		if (remaining.compareTo(amount) < 0)
			throw new IllegalArgumentException("可释放金额不足");
		r.setReleasedAmount(r.getReleasedAmount().add(amount));
		BigDecimal cashRemaining = defaultZero(r.getCashReservedAmount())
			.subtract(defaultZero(r.getCashConsumedAmount()))
			.subtract(defaultZero(r.getCashReleasedAmount()));
		BigDecimal cashRelease = cashRemaining.min(amount).max(BigDecimal.ZERO);
		BigDecimal creditRelease = amount.subtract(cashRelease);
		r.setCashReleasedAmount(defaultZero(r.getCashReleasedAmount()).add(cashRelease));
		r.setCreditReleasedAmount(defaultZero(r.getCreditReleasedAmount()).add(creditRelease));
		if (r.getReleasedAmount().add(r.getConsumedAmount()).compareTo(r.getReservedAmount()) >= 0) {
			r.setStatus("RELEASED");
			r.setReleasedAt(LocalDateTime.now());
		}
		enforceOptimisticUpdate(r);

		FinAccount reserve = getOrCreateAccount(orgId, "RESERVATION");
		reserve.setBalance(reserve.getBalance().subtract(amount));
		enforceOptimisticUpdate(reserve);

		FinAccount cash = getOrCreateAccount(orgId, "CASH");
		FinAccount credit = getOrCreateAccount(orgId, "CREDIT");
		cash.setBalance(cash.getBalance().add(cashRelease));
		credit.setBalance(credit.getBalance().add(creditRelease));
		if (cashRelease.signum() > 0) {
			enforceOptimisticUpdate(cash);
		}
		if (creditRelease.signum() > 0) {
			enforceOptimisticUpdate(credit);
			adjustCreditUsage(orgId, creditRelease.negate());
		}
		List<FinEntryBuilder> entries = new ArrayList<>();
		if (cashRelease.signum() > 0) {
			entries.add(e(cash.getId(), "DEBIT", cashRelease, cash.getBalance()));
		}
		if (creditRelease.signum() > 0) {
			entries.add(e(credit.getId(), "DEBIT", creditRelease, credit.getBalance()));
		}
		entries.add(e(reserve.getId(), "CREDIT", amount, reserve.getBalance()));
		return post("BUDGET_RELEASE", "BUDGET_RELEASE", businessId, orgId, memo,
				entries.toArray(FinEntryBuilder[]::new));
	}

	// ── Expire credits ──

	@Transactional
	public int expireCredits() {
		var expired = creditLineMapper.selectList(Wrappers.<CreditLine>lambdaQuery()
			.eq(CreditLine::getStatus, "ACTIVE")
			.lt(CreditLine::getExpiredAt, LocalDateTime.now()));
		int count = 0;
		for (CreditLine c : expired) {
			if (defaultZero(c.getUsedAmount()).signum() == 0) {
				settleUnusedCredit(c, "EXPIRED", "CREDIT_EXPIRE_" + c.getId());
				count++;
			}
		}
		return count;
	}

	@Transactional
	public CreditLine revokeCredit(String id, String businessId) {
		CreditLine line = creditLineMapper.selectById(id);
		if (line == null || !scopeService.canRead(line.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		scopeService.requireWrite(line.getOrgId());
		checkIdempotent("CREDIT_REVOKE", businessId);
		if (!"ACTIVE".equals(line.getStatus())) {
			throw new IllegalArgumentException("授信不是生效状态");
		}
		if (defaultZero(line.getUsedAmount()).signum() > 0) {
			throw new IllegalArgumentException("授信仍被预算预留占用");
		}
		settleUnusedCredit(line, "REVOKED", businessId);
		return line;
	}

	// ── Query ──

	public FinJournal requireJournal(String id) {
		FinJournal j = journalMapper.selectById(id);
		if (j == null || !scopeService.canRead(j.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return j;
	}

	public java.util.List<FinEntry> listEntries(String journalId) {
		requireJournal(journalId);
		return entryMapper.selectList(Wrappers.<FinEntry>lambdaQuery().eq(FinEntry::getJournalId, journalId));
	}

	public java.util.List<FinJournal> listJournals(String orgId, String journalType, int limit) {
		scopeService.requireRead(orgId);
		var q = Wrappers.<FinJournal>lambdaQuery()
			.eq(FinJournal::getOrgId, orgId)
			.eq(journalType != null, FinJournal::getJournalType, journalType)
			.orderByDesc(FinJournal::getPostedAt);
		if (limit > 0)
			q.last("LIMIT " + limit);
		return journalMapper.selectList(q);
	}

	public Page<FinJournal> pageJournals(Page<FinJournal> page, String orgId, String journalType) {
		scopeService.requireRead(orgId);
		return journalMapper.selectPage(page,
				Wrappers.<FinJournal>lambdaQuery()
					.eq(FinJournal::getOrgId, orgId)
					.eq(journalType != null, FinJournal::getJournalType, journalType)
					.orderByDesc(FinJournal::getPostedAt)
					.orderByDesc(FinJournal::getId));
	}

	// ── Invariants ──

	public void verifyBalance(String journalId) {
		var entries = listEntries(journalId);
		BigDecimal d = entries.stream()
			.filter(e -> "DEBIT".equals(e.getDirection()))
			.map(FinEntry::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal c = entries.stream()
			.filter(e -> "CREDIT".equals(e.getDirection()))
			.map(FinEntry::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (d.compareTo(c) != 0)
			throw new IllegalStateException("借贷不平衡: D=" + d + " C=" + c);
	}

	// ── Helpers ──

	private BudgetReservation requireReservation(String id) {
		BudgetReservation r = reservationMapper.selectById(id);
		if (r == null || !"ACTIVE".equals(r.getStatus()))
			throw new IllegalArgumentException("预留不存在或已失效");
		return r;
	}

	private BudgetReservation requireReservationForConfirmation(String id) {
		BudgetReservation reservation = reservationMapper.selectById(id);
		if (reservation == null
				|| (!"ACTIVE".equals(reservation.getStatus()) && !"CONSUMED".equals(reservation.getStatus()))) {
			throw new IllegalArgumentException("预留不存在或已失效");
		}
		return reservation;
	}

	private void checkPositive(BigDecimal v, String label) {
		if (v == null || v.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException(label + "必须大于零");
	}

	private void checkIdempotent(String businessType, String businessId) {
		if (businessId == null || businessId.isBlank()) {
			throw new IllegalArgumentException("业务幂等键不能为空");
		}
		if (journalMapper.selectCount(Wrappers.<FinJournal>lambdaQuery()
			.eq(FinJournal::getBusinessType, businessType)
			.eq(FinJournal::getBusinessId, businessId)) > 0)
			throw new IllegalArgumentException("业务重复: " + businessType + "/" + businessId);
	}

	private void enforceOptimisticUpdate(FinAccount a) {
		if (a.getVersion() == null) {
			a.setVersion(0);
		}
		int rows = accountMapper.updateById(a);
		if (rows == 0)
			throw new IllegalStateException("账户并发冲突: " + a.getId());
	}

	private void enforceOptimisticUpdate(BudgetReservation r) {
		if (r.getVersion() == null) {
			r.setVersion(0);
		}
		int rows = reservationMapper.updateById(r);
		if (rows == 0)
			throw new IllegalStateException("预留并发冲突: " + r.getId());
	}

	private FinJournal post(String journalType, String bizType, String bizId, String orgId, String memo,
			FinEntryBuilder... entries) {
		String jid = UUID.randomUUID().toString().replace("-", "");
		BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
		for (FinEntryBuilder eb : entries) {
			checkPositive(eb.amt, "分录金额");
			if ("DEBIT".equals(eb.dir)) {
				td = td.add(eb.amt);
			}
			else if ("CREDIT".equals(eb.dir)) {
				tc = tc.add(eb.amt);
			}
			else {
				throw new IllegalArgumentException("无效分录方向");
			}
		}
		if (td.compareTo(tc) != 0)
			throw new IllegalStateException("借贷不平衡: D=" + td + " C=" + tc);
		FinJournal j = new FinJournal();
		j.setId(jid);
		j.setJournalType(journalType);
		j.setBusinessType(bizType);
		j.setBusinessId(bizId);
		j.setOrgId(orgId);
		j.setCurrency("CNY");
		j.setTotalDebit(td);
		j.setTotalCredit(tc);
		j.setStatus("POSTED");
		j.setMemo(memo);
		j.setPostedAt(LocalDateTime.now());
		journalMapper.insert(j);
		for (FinEntryBuilder eb : entries) {
			FinEntry entry = new FinEntry();
			entry.setJournalId(jid);
			entry.setFinAccountId(eb.aid);
			entry.setDirection(eb.dir);
			entry.setAmount(eb.amt);
			entry.setBalanceAfter(eb.bal);
			entry.setMemo(memo);
			entryMapper.insert(entry);
		}
		return j;
	}

	private void requireReservationOrganization(BudgetReservation reservation, String orgId) {
		if (!orgId.equals(reservation.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

	private void adjustCreditUsage(String orgId, BigDecimal delta) {
		CreditLine line = creditLineMapper.selectOne(Wrappers.<CreditLine>lambdaQuery()
			.eq(CreditLine::getOrgId, orgId)
			.eq(CreditLine::getStatus, "ACTIVE")
			.last("LIMIT 1"));
		if (line == null) {
			if (delta.signum() > 0) {
				throw new IllegalStateException("授信账户缺少生效授信记录");
			}
			return;
		}
		BigDecimal used = defaultZero(line.getUsedAmount()).add(delta);
		if (used.signum() < 0 || used.compareTo(line.getCreditAmount()) > 0) {
			throw new IllegalStateException("授信占用不变量失败");
		}
		line.setUsedAmount(used);
		line.setAvailableAmount(line.getCreditAmount().subtract(used));
		creditLineMapper.updateById(line);
	}

	private void settleUnusedCredit(CreditLine line, String targetStatus, String businessId) {
		BigDecimal available = defaultZero(line.getAvailableAmount());
		if (available.signum() > 0) {
			FinAccount credit = getOrCreateAccount(line.getOrgId(), "CREDIT");
			FinAccount contra = getOrCreateAccount(line.getOrgId(), "REFUND_PAYABLE");
			if (credit.getBalance().compareTo(available) < 0) {
				throw new IllegalStateException("授信账户余额不变量失败");
			}
			credit.setBalance(credit.getBalance().subtract(available));
			contra.setBalance(contra.getBalance().add(available));
			enforceOptimisticUpdate(credit);
			enforceOptimisticUpdate(contra);
			post("CREDIT_" + targetStatus, "CREDIT_" + targetStatus, businessId, line.getOrgId(), "授信" + targetStatus,
					e(contra.getId(), "DEBIT", available, contra.getBalance()),
					e(credit.getId(), "CREDIT", available, credit.getBalance()));
		}
		line.setAvailableAmount(BigDecimal.ZERO);
		line.setStatus(targetStatus);
		creditLineMapper.updateById(line);
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static FinEntryBuilder e(String aid, String dir, BigDecimal amt, BigDecimal bal) {
		return new FinEntryBuilder(aid, dir, amt, bal);
	}

	private record FinEntryBuilder(String aid, String dir, BigDecimal amt, BigDecimal bal) {
	}

}
