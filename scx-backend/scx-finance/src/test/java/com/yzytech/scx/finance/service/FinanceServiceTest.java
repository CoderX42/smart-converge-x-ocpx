package com.yzytech.scx.finance.service;

import com.yzytech.scx.commercial.port.CommercialAccountPort;
import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.finance.entity.*;
import com.yzytech.scx.finance.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceServiceTest {

	private final FinAccountMapper accountMapper = mock(FinAccountMapper.class);

	private final FinJournalMapper journalMapper = mock(FinJournalMapper.class);

	private final FinEntryMapper entryMapper = mock(FinEntryMapper.class);

	private final CreditLineMapper creditLineMapper = mock(CreditLineMapper.class);

	private final BudgetReservationMapper reservationMapper = mock(BudgetReservationMapper.class);

	private final OrganizationScopeService scopeService = mock(OrganizationScopeService.class);

	private final CommercialAccountPort commercialAccountPort = mock(CommercialAccountPort.class);

	private final FinanceService service = new FinanceService(accountMapper, journalMapper, entryMapper,
			creditLineMapper, reservationMapper, scopeService, commercialAccountPort);

	private FinAccount cash, contra, reserve, est, confirmed;

	@BeforeEach
	void setUp() {
		reset(accountMapper, journalMapper, entryMapper, creditLineMapper, reservationMapper, scopeService,
				commercialAccountPort);
		when(scopeService.canRead(any())).thenReturn(true);
		cash = account("acct-cash", "org-1", "CASH", new BigDecimal("1000.000000"), 0);
		contra = account("acct-contra", "org-1", "REFUND_PAYABLE", BigDecimal.ZERO, 0);
		reserve = account("acct-res", "org-1", "RESERVATION", BigDecimal.ZERO, 0);
		est = account("acct-est", "org-1", "ESTIMATED_SPEND", BigDecimal.ZERO, 0);
		confirmed = account("acct-conf", "org-1", "CONFIRMED_SPEND", BigDecimal.ZERO, 0);
		// Default: journal doesn't exist (not duplicate)
		when(journalMapper.selectCount(any())).thenReturn(0L);
		// Default: update succeeds
		when(accountMapper.updateById(any(FinAccount.class))).thenReturn(1);
		when(reservationMapper.updateById(any(BudgetReservation.class))).thenReturn(1);
	}

	// ── Invariant 1: sum(debit) == sum(credit) ──

	@Test
	void rechargeProducesBalancedJournal() {
		mockSelectOne(cash, contra); // first cash, then contra
		FinJournal j = service.recharge("org-1", new BigDecimal("100"), "biz-1", "充值");
		assertThat(j.getTotalDebit()).isEqualByComparingTo(j.getTotalCredit());
	}

	@Test
	void giftProducesBalancedJournal() {
		mockSelectOne(cash, contra);
		FinJournal j = service.gift("org-1", new BigDecimal("50"), "biz-2", "赠送");
		assertThat(j.getTotalDebit()).isEqualByComparingTo(j.getTotalCredit());
	}

	// ── Invariant 2: Same idempotency key rejected ──

	@Test
	void idempotentKeyRejectsDuplicate() {
		mockSelectOne(cash, contra);
		when(journalMapper.selectCount(any())).thenReturn(1L);
		assertThatThrownBy(() -> service.recharge("org-1", new BigDecimal("100"), "dup", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("业务重复");
	}

	// ── Invariant 3: available >= 0 ──

	@Test
	void reserveBudgetRejectedWhenBalanceInsufficient() {
		// Only 10 in cash, trying to reserve 100
		FinAccount lowCash = account("acct-cash", "org-1", "CASH", new BigDecimal("10"), 0);
		mockSelectOne(lowCash);
		assertThatThrownBy(() -> service.reserveBudget("org-1", new BigDecimal("100"), null, "biz-res", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不足");
	}

	// ── Invariant 4: Cannot release more than remaining ──

	@Test
	void releaseExceedingRemainingFails() {
		BudgetReservation r = reservation("res-1", "org-1", 100, 80, 10);
		when(reservationMapper.selectById("res-1")).thenReturn(r);
		// Remaining = 100 - 80 - 10 = 10, trying release 50
		assertThatThrownBy(() -> service.releaseReservation("org-1", "res-1", new BigDecimal("50"), "biz-rel", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("可释放金额不足");
	}

	// ── Invariant 5: Reservation becomes RELEASED when fully settled ──

	@Test
	void releaseFullRemainingCompletesReservation() {
		BudgetReservation r = reservation("res-1", "org-1", 100, 30, 0);
		when(reservationMapper.selectById("res-1")).thenReturn(r);
		// Mock journal not duplicate
		when(journalMapper.selectCount(any())).thenReturn(0L);
		// Need: reservation account, cash account, and credit account.
		FinAccount cash2 = account("acct-cash", "org-1", "CASH", new BigDecimal("0"), 0);
		FinAccount reserve2 = account("acct-res", "org-1", "RESERVATION", new BigDecimal("100"), 0);
		FinAccount credit2 = account("acct-credit", "org-1", "CREDIT", BigDecimal.ZERO, 0);
		r.setCashReservedAmount(new BigDecimal("70"));
		r.setCashConsumedAmount(BigDecimal.ZERO);
		r.setCashReleasedAmount(BigDecimal.ZERO);
		mockSelectOne(reserve2, cash2, credit2);

		service.releaseReservation("org-1", "res-1", new BigDecimal("70"), "biz-full", null);
		assertThat(r.getStatus()).isEqualTo("RELEASED");
	}

	// ── Invariant 6: Confirmed spend checks estimated balance ──

	@Test
	void confirmSpendRejectedWhenEstimatedInsufficient() {
		// est balance is only 5, trying to confirm 50
		FinAccount estLow = account("acct-est", "org-1", "ESTIMATED_SPEND", new BigDecimal("5"), 0);
		FinAccount conf = account("acct-conf", "org-1", "CONFIRMED_SPEND", BigDecimal.ZERO, 0);
		BudgetReservation r = reservation("res-1", "org-1", 100, 50, 0);
		when(reservationMapper.selectById("res-1")).thenReturn(r);
		mockSelectOne(estLow, conf);
		assertThatThrownBy(() -> service.confirmSpend("org-1", "res-1", new BigDecimal("50"), "biz-conf", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不足");
	}

	// ── Helpers ──

	@SafeVarargs
	private void mockSelectOne(FinAccount first, FinAccount... rest) {
		var stub = when(accountMapper.selectOne(any())).thenReturn(first);
		for (FinAccount a : rest)
			stub = stub.thenReturn(a);
	}

	private static FinAccount account(String id, String orgId, String type, BigDecimal balance, int version) {
		FinAccount a = new FinAccount();
		a.setId(id);
		a.setOrgId(orgId);
		a.setAccountType(type);
		a.setBalance(balance);
		a.setVersion(version);
		a.setStatus("ACTIVE");
		return a;
	}

	private static BudgetReservation reservation(String id, String orgId, int reserved, int consumed, int released) {
		BudgetReservation r = new BudgetReservation();
		r.setId(id);
		r.setOrgId(orgId);
		r.setReservedAmount(new BigDecimal(reserved));
		r.setConsumedAmount(new BigDecimal(consumed));
		r.setReleasedAmount(new BigDecimal(released));
		r.setStatus("ACTIVE");
		r.setVersion(0);
		return r;
	}

}
