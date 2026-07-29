package com.yzytech.scx.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.finance.entity.*;
import com.yzytech.scx.finance.service.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/finance")
@Tag(name = "财务账本", description = "虚拟充值、授信、预算预留、消耗与余额查询")
public class FinanceController {

	private final FinanceService financeService;

	@PostMapping("/recharge")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "虚拟充值")
	public Result<FinJournal> recharge(@RequestParam String orgId, @RequestParam BigDecimal amount,
			@RequestParam String businessId, @RequestParam(required = false) String memo) {
		return call(() -> financeService.recharge(orgId, amount, businessId, memo));
	}

	@PostMapping("/gift")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "虚拟赠送")
	public Result<FinJournal> gift(@RequestParam String orgId, @RequestParam BigDecimal amount,
			@RequestParam String businessId, @RequestParam(required = false) String memo) {
		return call(() -> financeService.gift(orgId, amount, businessId, memo));
	}

	@PostMapping("/credit-lines")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "授信")
	public Result<CreditLine> grantCredit(@RequestParam String orgId, @RequestParam BigDecimal amount,
			@RequestParam String businessId) {
		return call(() -> financeService.grantCredit(orgId, amount, java.time.LocalDateTime.now().plusDays(90),
				businessId));
	}

	@PostMapping("/budget-reservations")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "预算预留")
	public Result<BudgetReservation> reserve(@RequestParam String orgId, @RequestParam BigDecimal amount,
			@RequestParam(required = false) String adAccountId, @RequestParam String businessId) {
		return call(() -> financeService.reserveBudget(orgId, amount, adAccountId, businessId, null));
	}

	@GetMapping("/budget-reservations")
	@SaCheckPermission("finance:ledger:read")
	@Operation(summary = "查询预算预留列表")
	public Result<java.util.List<BudgetReservation>> reservations(@RequestParam String orgId) {
		return call(() -> financeService.listReservations(orgId, null));
	}

	@PostMapping("/credit-lines/{id}/revoke")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "撤销未占用授信")
	public Result<CreditLine> revokeCredit(@PathVariable String id, @RequestParam String businessId) {
		return call(() -> financeService.revokeCredit(id, businessId));
	}

	@PostMapping("/budget-reservations/{id}/estimate-spend")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "估算消耗")
	public Result<FinJournal> estimateSpend(@PathVariable String id, @RequestParam String orgId,
			@RequestParam BigDecimal amount, @RequestParam String businessId) {
		return call(() -> financeService.estimateSpend(orgId, id, amount, businessId, null));
	}

	@PostMapping("/budget-reservations/{id}/consume")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "消耗预留（同estimate-spend）")
	public Result<FinJournal> consume(@PathVariable String id, @RequestParam String orgId,
			@RequestParam BigDecimal amount, @RequestParam String businessId) {
		return call(() -> financeService.estimateSpend(orgId, id, amount, businessId, null));
	}

	@PostMapping("/budget-reservations/{id}/confirm-spend")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "确认消耗")
	public Result<FinJournal> confirmSpend(@PathVariable String id, @RequestParam String orgId,
			@RequestParam BigDecimal amount, @RequestParam String businessId) {
		return call(() -> financeService.confirmSpend(orgId, id, amount, businessId, null));
	}

	@PostMapping("/budget-reservations/{id}/release")
	@SaCheckPermission("finance:ledger:write")
	@Operation(summary = "释放预留")
	public Result<FinJournal> release(@PathVariable String id, @RequestParam String orgId,
			@RequestParam BigDecimal amount, @RequestParam String businessId) {
		return call(() -> financeService.releaseReservation(orgId, id, amount, businessId, null));
	}

	@GetMapping("/accounts")
	@SaCheckPermission("finance:ledger:read")
	@Operation(summary = "查询财务账户列表")
	public Result<java.util.List<FinAccount>> accounts(@RequestParam String orgId) {
		return call(() -> financeService.listAccounts(orgId));
	}

	@GetMapping("/accounts/page")
	@SaCheckPermission("finance:ledger:read")
	public Result<Page<FinAccount>> pageAccounts(Page<FinAccount> page, @RequestParam String orgId,
			@RequestParam(required = false) String accountType) {
		return call(() -> financeService.pageAccounts(page, orgId, accountType));
	}

	@GetMapping("/balance")
	@SaCheckPermission("finance:ledger:read")
	@Operation(summary = "查询可用余额")
	public Result<BigDecimal> balance(@RequestParam String orgId) {
		return call(() -> financeService.getAvailableBalance(orgId));
	}

	@GetMapping("/journals")
	@SaCheckPermission("finance:ledger:read")
	@Operation(summary = "查询日记账")
	public Result<java.util.List<FinJournal>> journals(@RequestParam String orgId,
			@RequestParam(required = false) String type) {
		return call(() -> financeService.listJournals(orgId, type, 50));
	}

	@GetMapping("/journals/page")
	@SaCheckPermission("finance:ledger:read")
	public Result<Page<FinJournal>> pageJournals(Page<FinJournal> page, @RequestParam String orgId,
			@RequestParam(required = false) String type) {
		return call(() -> financeService.pageJournals(page, orgId, type));
	}

	@GetMapping("/journals/{id}/entries")
	@SaCheckPermission("finance:ledger:read")
	public Result<java.util.List<FinEntry>> entries(@PathVariable String id) {
		return call(() -> financeService.listEntries(id));
	}

	@GetMapping("/credit-lines")
	@SaCheckPermission("finance:ledger:read")
	public Result<Page<CreditLine>> creditLines(Page<CreditLine> page, @RequestParam String orgId,
			@RequestParam(required = false) String status) {
		return call(() -> financeService.pageCreditLines(page, orgId, status));
	}

	@GetMapping("/budget-reservations/page")
	@SaCheckPermission("finance:ledger:read")
	public Result<Page<BudgetReservation>> pageReservations(Page<BudgetReservation> page, @RequestParam String orgId,
			@RequestParam(required = false) String status) {
		return call(() -> financeService.pageReservations(page, orgId, status));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
