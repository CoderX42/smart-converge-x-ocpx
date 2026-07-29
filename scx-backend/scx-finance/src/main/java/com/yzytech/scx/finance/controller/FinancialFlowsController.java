package com.yzytech.scx.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.finance.entity.*;
import com.yzytech.scx.finance.service.FinancialFlowsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/finance/flows")
public class FinancialFlowsController {

	private final FinancialFlowsService service;

	@GetMapping("/rebate-rules")
	@SaCheckPermission("finance:rebate:read")
	public Result<Page<RebateRule>> rules(Page<RebateRule> page, String status) {
		return call(() -> service.pageRules(page, status));
	}

	@PostMapping("/rebate-rules")
	@SaCheckPermission("finance:rebate:write")
	public Result<RebateRule> createRule(@RequestParam String name, @RequestParam String ruleType,
			@RequestParam String conditionTree, @RequestParam BigDecimal rate) {
		return call(() -> service.createRule(name, ruleType, conditionTree, rate));
	}

	@PostMapping("/rebate-rules/{id}/activate")
	@SaCheckPermission("finance:rebate:write")
	public Result<RebateRule> activateRule(@PathVariable String id) {
		return call(() -> service.activateRule(id));
	}

	@GetMapping("/rebate-calculations")
	@SaCheckPermission("finance:rebate:read")
	public Result<Page<RebateCalculation>> calculations(Page<RebateCalculation> page, @RequestParam String orgId) {
		return call(() -> service.pageCalculations(page, orgId));
	}

	@PostMapping("/rebate-calculations")
	@SaCheckPermission("finance:rebate:write")
	public Result<RebateCalculation> calculate(@RequestParam String orgId, @RequestParam String ruleId,
			@RequestParam LocalDate start, @RequestParam LocalDate end) {
		return call(() -> service.calculateRebate(orgId, ruleId, start, end));
	}

	@GetMapping("/refunds")
	@SaCheckPermission("finance:refund:read")
	public Result<Page<RefundCase>> refunds(Page<RefundCase> page, @RequestParam String orgId, String status) {
		return call(() -> service.pageRefunds(page, orgId, status));
	}

	@GetMapping("/refunds/{id}")
	@SaCheckPermission("finance:refund:read")
	public Result<RefundCase> refund(@PathVariable String id) {
		return call(() -> service.getRefund(id));
	}

	@PostMapping("/refunds")
	@SaCheckPermission("finance:refund:write")
	public Result<RefundCase> createRefund(@RequestParam String orgId, @RequestParam BigDecimal amount,
			@RequestParam String reason) {
		return call(() -> service.createRefund(orgId, amount, reason));
	}

	@PostMapping("/refunds/{id}/submit")
	@SaCheckPermission("finance:refund:write")
	public Result<RefundCase> submitRefund(@PathVariable String id) {
		return call(() -> service.submitRefund(id));
	}

	@PostMapping("/refunds/{id}/review")
	@SaCheckPermission("finance:refund:review")
	public Result<RefundCase> reviewRefund(@PathVariable String id, @RequestParam String decision) {
		return call(() -> service.reviewRefund(id, decision));
	}

	@PostMapping("/refunds/{id}/reverse")
	@SaCheckPermission("finance:adjustment:write")
	public Result<RefundCase> reverseRefund(@PathVariable String id) {
		return call(() -> service.reverseRefund(id));
	}

	@GetMapping("/invoices")
	@SaCheckPermission("finance:invoice:read")
	public Result<Page<InvoiceApplication>> invoices(Page<InvoiceApplication> page, @RequestParam String orgId,
			String status) {
		return call(() -> service.pageInvoices(page, orgId, status));
	}

	@GetMapping("/invoices/{id}")
	@SaCheckPermission("finance:invoice:read")
	public Result<InvoiceApplication> invoice(@PathVariable String id) {
		return call(() -> service.getInvoice(id));
	}

	@PostMapping("/invoices")
	@SaCheckPermission("finance:invoice:write")
	public Result<InvoiceApplication> createInvoice(@RequestParam String orgId, @RequestParam BigDecimal amount) {
		return call(() -> service.createInvoice(orgId, amount));
	}

	@PostMapping("/invoices/{id}/issue")
	@SaCheckPermission("finance:invoice:write")
	public Result<InvoiceApplication> issueInvoice(@PathVariable String id) {
		return call(() -> service.issueInvoice(id));
	}

	@GetMapping("/reconciliations")
	@SaCheckPermission("finance:reconciliation:read")
	public Result<Page<ReconciliationRun>> reconciliations(Page<ReconciliationRun> page, @RequestParam String orgId,
			String status) {
		return call(() -> service.pageReconciliations(page, orgId, status));
	}

	@GetMapping("/reconciliations/{id}")
	@SaCheckPermission("finance:reconciliation:read")
	public Result<ReconciliationRun> reconciliation(@PathVariable String id) {
		return call(() -> service.getReconciliation(id));
	}

	@PostMapping("/reconciliations")
	@SaCheckPermission("finance:reconciliation:write")
	public Result<ReconciliationRun> reconcile(@RequestParam String orgId, @RequestParam LocalDate start,
			@RequestParam LocalDate end) {
		return call(() -> service.startReconciliation(orgId, start, end));
	}

	@PostMapping("/reconciliations/{id}/resolve")
	@SaCheckPermission("finance:reconciliation:write")
	public Result<ReconciliationRun> resolve(@PathVariable String id, @RequestParam String resolution) {
		return call(() -> service.resolveReconciliation(id, resolution));
	}

	@GetMapping("/closing-periods")
	@SaCheckPermission("finance:closing:read")
	public Result<Page<ClosingPeriod>> closingPeriods(Page<ClosingPeriod> page, @RequestParam String orgId,
			String status) {
		return call(() -> service.pageClosingPeriods(page, orgId, status));
	}

	@GetMapping("/closing-periods/{id}")
	@SaCheckPermission("finance:closing:read")
	public Result<ClosingPeriod> closingPeriod(@PathVariable String id) {
		return call(() -> service.getClosingPeriod(id));
	}

	@PostMapping("/closing-periods")
	@SaCheckPermission("finance:closing:write")
	public Result<ClosingPeriod> openPeriod(@RequestParam String orgId, @RequestParam LocalDate start,
			@RequestParam LocalDate end) {
		return call(() -> service.openClosingPeriod(orgId, start, end));
	}

	@PostMapping("/closing-periods/{id}/close")
	@SaCheckPermission("finance:closing:write")
	public Result<ClosingPeriod> closePeriod(@PathVariable String id, @RequestParam BigDecimal maxDiffAllowed) {
		return call(() -> service.closePeriod(id, maxDiffAllowed));
	}

	@GetMapping("/adjustments")
	@SaCheckPermission("finance:adjustment:read")
	public Result<Page<AdjustmentJournal>> adjustments(Page<AdjustmentJournal> page, @RequestParam String orgId) {
		return call(() -> service.pageAdjustments(page, orgId));
	}

	@GetMapping("/adjustments/{id}")
	@SaCheckPermission("finance:adjustment:read")
	public Result<AdjustmentJournal> adjustment(@PathVariable String id) {
		return call(() -> service.getAdjustment(id));
	}

	@PostMapping("/adjustments/{id}/reverse")
	@SaCheckPermission("finance:adjustment:write")
	public Result<AdjustmentJournal> reverseAdjustment(@PathVariable String id) {
		return call(() -> service.reverseAdjustment(id));
	}

	@PostMapping("/adjustments")
	@SaCheckPermission("finance:adjustment:write")
	public Result<AdjustmentJournal> adjustment(@RequestParam String orgId, @RequestParam String closingPeriodId,
			@RequestParam String type, @RequestParam BigDecimal amount, @RequestParam String reason) {
		return call(() -> service.addAdjustment(orgId, closingPeriodId, type, amount, reason));
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
