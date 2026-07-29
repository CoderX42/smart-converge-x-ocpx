package com.yzytech.scx.finance.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.finance.entity.*;
import com.yzytech.scx.finance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Financial sandbox flows: rebate, refund, invoice, reconciliation, closing &
 * adjustments.
 */
@Service
@RequiredArgsConstructor
public class FinancialFlowsService {

	private final RebateRuleMapper ruleMapper;

	private final RebateRuleVersionMapper ruleVersionMapper;

	private final RebateCalculationMapper calcMapper;

	private final RefundCaseMapper refundMapper;

	private final InvoiceApplicationMapper invoiceMapper;

	private final InvoiceItemMapper invoiceItemMapper;

	private final ReconciliationRunMapper reconMapper;

	private final ReconciliationItemMapper reconItemMapper;

	private final ClosingPeriodMapper closingMapper;

	private final AdjustmentJournalMapper adjustmentMapper;

	private final FinanceService financeService;

	private final OrganizationScopeService scopeService;

	// ── Rebate ──

	@Transactional
	public RebateRule createRule(String name, String ruleType, String conditionTree, BigDecimal rate) {
		RebateRule r = new RebateRule();
		r.setRuleName(name);
		r.setRuleType(ruleType);
		r.setConditionTree(conditionTree);
		r.setRate(rate);
		r.setStatus("DRAFT");
		ruleMapper.insert(r);
		return r;
	}

	@Transactional
	public RebateRule activateRule(String ruleId) {
		RebateRule r = requireRule(ruleId);
		if (!"DRAFT".equals(r.getStatus()))
			throw new IllegalArgumentException("只有草稿规则可以激活");
		r.setStatus("ACTIVE");
		r.setEffectiveFrom(LocalDate.now());
		ruleMapper.updateById(r);
		// Snapshot an immutable version
		RebateRuleVersion v = new RebateRuleVersion();
		v.setRuleId(r.getId());
		v.setVersion(ruleVersionCount(r.getId()) + 1);
		v.setConditionTree(r.getConditionTree());
		v.setRate(r.getRate());
		ruleVersionMapper.insert(v);
		return r;
	}

	@Transactional
	public RebateRuleVersion reviseRule(String ruleId, String newCondition, BigDecimal newRate) {
		RebateRule r = requireRule(ruleId);
		if (!"ACTIVE".equals(r.getStatus()) && !"ARCHIVED".equals(r.getStatus()))
			throw new IllegalArgumentException("只有生效或已归档规则可以修订");
		int nextVer = ruleVersionCount(ruleId) + 1;
		RebateRuleVersion v = new RebateRuleVersion();
		v.setRuleId(ruleId);
		v.setVersion(nextVer);
		v.setConditionTree(newCondition);
		v.setRate(newRate);
		v.setSupersedes(null);
		ruleVersionMapper.insert(v);
		// New version + difference journal created by caller
		return v;
	}

	@Transactional
	public RebateCalculation calculateRebate(String orgId, String ruleId, LocalDate start, LocalDate end) {
		scopeService.requireWrite(orgId);
		requireValidPeriod(start, end);
		RebateRule rule = requireRule(ruleId);
		if (!"ACTIVE".equals(rule.getStatus()))
			throw new IllegalArgumentException("规则未生效");

		// Check no existing calculation for same period+rule
		if (calcMapper.selectCount(Wrappers.<RebateCalculation>lambdaQuery()
			.eq(RebateCalculation::getOrgId, orgId)
			.eq(RebateCalculation::getRuleId, ruleId)
			.eq(RebateCalculation::getPeriodStart, start)
			.eq(RebateCalculation::getPeriodEnd, end)
			.eq(RebateCalculation::getStatus, "POSTED")) > 0)
			throw new IllegalArgumentException("该期间已计算返点");

		BigDecimal confirmed = getConfirmedSpendForPeriod(orgId, start, end);
		BigDecimal rebateAmount = confirmed.multiply(rule.getRate() != null ? rule.getRate() : BigDecimal.ZERO);

		RebateCalculation calc = new RebateCalculation();
		calc.setOrgId(orgId);
		calc.setRuleId(ruleId);
		calc.setRuleVersion(ruleVersionCount(ruleId));
		calc.setBaseAmount(confirmed);
		calc.setRebateAmount(rebateAmount);
		calc.setPeriodStart(start);
		calc.setPeriodEnd(end);
		calc.setStatus("POSTED");
		calcMapper.insert(calc);

		// Post rebate to journal
		FinJournal journal = financeService.recharge(orgId, rebateAmount, "REBATE_" + calc.getId(),
				"返点:" + rule.getRuleName());
		calc.setJournalId(journal.getId());
		calcMapper.updateById(calc);
		return calc;
	}

	private BigDecimal getConfirmedSpendForPeriod(String orgId, LocalDate start, LocalDate end) {
		return financeService.getAccountBalance(orgId, "CONFIRMED_SPEND");
	}

	// ── Refund ──

	@Transactional
	public RefundCase createRefund(String orgId, BigDecimal amount, String reason) {
		scopeService.requireWrite(orgId);
		if (amount == null || amount.signum() <= 0)
			throw new IllegalArgumentException("退款金额必须大于零");
		if (!StringUtils.hasText(reason))
			throw new IllegalArgumentException("退款原因不能为空");
		BigDecimal refundable = financeService.getAccountBalance(orgId, "CASH");
		if (refundable.compareTo(amount) < 0)
			throw new IllegalArgumentException("可退款余额不足: " + refundable);

		RefundCase c = new RefundCase();
		c.setOrgId(orgId);
		c.setAmount(amount);
		c.setRefundReason(reason);
		c.setStatus("DRAFT");
		c.setMakerId(currentUserId());
		refundMapper.insert(c);
		return c;
	}

	@Transactional
	public RefundCase submitRefund(String id) {
		RefundCase c = requireRefund(id);
		if (!"DRAFT".equals(c.getStatus()))
			throw new IllegalArgumentException("只有草稿可以提交");
		c.setStatus("SUBMITTED");
		c.setSubmittedAt(LocalDateTime.now());
		refundMapper.updateById(c);
		return c;
	}

	@Transactional
	public RefundCase reviewRefund(String id, String decision, String checkerId) {
		RefundCase c = requireRefund(id);
		if (!"SUBMITTED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已提交案件可审批");
		if (checkerId.equals(c.getMakerId()))
			throw new IllegalArgumentException("审批人不能与经办人相同");
		c.setCheckerId(checkerId);
		c.setReviewedAt(LocalDateTime.now());

		switch (decision) {
			case "APPROVE" -> {
				c.setStatus("APPROVED");
				// Execute refund: deduct from cash
				FinJournal journal = financeService.refundCash(c.getOrgId(), c.getAmount(), "REFUND_" + c.getId(),
						"退款:" + c.getRefundReason());
				c.setJournalId(journal.getId());
				c.setStatus("COMPLETED");
				c.setCompletedAt(LocalDateTime.now());
			}
			case "REJECT" -> c.setStatus("REJECTED");
			default -> throw new IllegalArgumentException("无效审批决定: " + decision);
		}
		refundMapper.updateById(c);
		return c;
	}

	public RefundCase reviewRefund(String id, String decision) {
		return reviewRefund(id, decision, currentUserId());
	}

	@Transactional
	public RefundCase reverseRefund(String id) {
		RefundCase c = requireRefund(id);
		if (!"COMPLETED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已完成退款可冲正");
		// Reverse the refund
		FinJournal journal = financeService.recharge(c.getOrgId(), c.getAmount(), "REVERSE_REFUND_" + c.getId(),
				"退款冲正:" + c.getRefundReason());
		c.setReversalJournalId(journal.getId());
		c.setStatus("REVERSED");
		refundMapper.updateById(c);
		return c;
	}

	// ── Invoice ──

	@Transactional
	public InvoiceApplication createInvoice(String orgId, BigDecimal amount) {
		scopeService.requireWrite(orgId);
		if (amount == null || amount.signum() <= 0)
			throw new IllegalArgumentException("开票金额必须大于零");
		// Prevent double-invoicing of settled amounts
		BigDecimal settled = financeService.getAccountBalance(orgId, "CONFIRMED_SPEND");
		BigDecimal alreadyInvoiced = getInvoicedTotal(orgId);
		BigDecimal available = settled.subtract(alreadyInvoiced);
		if (available.compareTo(amount) < 0)
			throw new IllegalArgumentException("可开票金额不足: available=" + available + ", requested=" + amount);

		InvoiceApplication app = new InvoiceApplication();
		app.setOrgId(orgId);
		app.setInvoiceAmount(amount);
		app.setSettledAmount(settled);
		app.setInvoiceStatus("DRAFT");
		invoiceMapper.insert(app);
		return app;
	}

	@Transactional
	public InvoiceApplication issueInvoice(String id) {
		InvoiceApplication app = requireInvoice(id);
		if (!"DRAFT".equals(app.getInvoiceStatus()) && !"SUBMITTED".equals(app.getInvoiceStatus()))
			throw new IllegalArgumentException("当前状态不可开票");
		app.setInvoiceStatus("ISSUED");
		app.setInvoiceNo("INV-SIM-" + System.currentTimeMillis());
		app.setIssuedAt(LocalDateTime.now());
		invoiceMapper.updateById(app);
		return app;
	}

	private BigDecimal getInvoicedTotal(String orgId) {
		return invoiceMapper
			.selectList(Wrappers.<InvoiceApplication>lambdaQuery()
				.eq(InvoiceApplication::getOrgId, orgId)
				.in(InvoiceApplication::getInvoiceStatus, "SUBMITTED", "ISSUED"))
			.stream()
			.map(InvoiceApplication::getInvoiceAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// ── Reconciliation ──

	@Transactional
	public ReconciliationRun startReconciliation(String orgId, LocalDate start, LocalDate end) {
		scopeService.requireWrite(orgId);
		requireValidPeriod(start, end);
		String inputHash = computeInputHash(orgId, start, end);
		BigDecimal scxBalance = financeService.getAvailableBalance(orgId);

		ReconciliationRun run = new ReconciliationRun();
		run.setOrgId(orgId);
		run.setPeriodStart(start);
		run.setPeriodEnd(end);
		run.setTimezone("Asia/Shanghai");
		run.setInputHash(inputHash);
		run.setScxBalance(scxBalance);
		run.setStatus("OPEN");
		reconMapper.insert(run);
		return run;
	}

	@Transactional
	public ReconciliationRun resolveReconciliation(String runId, String resolution) {
		ReconciliationRun run = requireRecon(runId);
		if (!"OPEN".equals(run.getStatus()) && !"MISMATCHED".equals(run.getStatus()))
			throw new IllegalArgumentException("对账状态不可解决");
		run.setStatus(resolution);
		run.setResolvedAt(LocalDateTime.now());
		reconMapper.updateById(run);
		return run;
	}

	// ── Closing ──

	@Transactional
	public ClosingPeriod openClosingPeriod(String orgId, LocalDate start, LocalDate end) {
		scopeService.requireWrite(orgId);
		requireValidPeriod(start, end);
		ClosingPeriod cp = new ClosingPeriod();
		cp.setOrgId(orgId);
		cp.setPeriodStart(start);
		cp.setPeriodEnd(end);
		cp.setTimezone("Asia/Shanghai");
		cp.setStatus("OPEN");
		cp.setDiffThreshold(new BigDecimal("0.000000"));
		closingMapper.insert(cp);
		return cp;
	}

	@Transactional
	public ClosingPeriod closePeriod(String periodId, BigDecimal maxDiffAllowed) {
		ClosingPeriod cp = requireClosingPeriod(periodId);
		if (!"OPEN".equals(cp.getStatus()))
			throw new IllegalArgumentException("期间不是OPEN状态");

		// Check reconciliation differences
		BigDecimal totalDiff = getTotalReconciliationDiff(cp.getOrgId(), cp.getPeriodStart(), cp.getPeriodEnd());
		if (maxDiffAllowed != null && totalDiff.abs().compareTo(maxDiffAllowed) > 0)
			throw new IllegalArgumentException("对账差异超阈值: diff=" + totalDiff + " > " + maxDiffAllowed);

		String hash = computeInputHash(cp.getOrgId(), cp.getPeriodStart(), cp.getPeriodEnd());
		cp.setInputHash(hash);
		cp.setStatus("CLOSED");
		cp.setClosedAt(LocalDateTime.now());
		cp.setClosedBy(currentUserId());
		closingMapper.updateById(cp);
		return cp;
	}

	// ── Adjustment (post-closing) ──

	@Transactional
	public AdjustmentJournal addAdjustment(String orgId, String closingPeriodId, String type, BigDecimal amount,
			String reason) {
		scopeService.requireWrite(orgId);
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("调整金额必须大于零");
		}
		ClosingPeriod closingPeriod = requireClosingPeriod(closingPeriodId);
		if (!orgId.equals(closingPeriod.getOrgId()))
			throw new IllegalArgumentException("关账期间不属于当前组织");
		if (!"CLOSED".equals(closingPeriod.getStatus()))
			throw new IllegalArgumentException("只有已关账期间可追加调整");

		AdjustmentJournal adj = new AdjustmentJournal();
		adj.setId(UUID.randomUUID().toString().replace("-", ""));
		adj.setOrgId(orgId);
		adj.setClosingPeriodId(closingPeriodId);
		adj.setAdjustmentType(type);
		adj.setReason(reason);
		adj.setAmount(amount);
		adj.setApprovedBy(currentUserId());
		FinJournal journal = financeService.recharge(orgId, amount, "ADJ_" + adj.getId(), "调整:" + reason);
		adj.setJournalId(journal.getId());
		adjustmentMapper.insert(adj);
		return adj;
	}

	@Transactional
	public AdjustmentJournal reverseAdjustment(String adjId) {
		AdjustmentJournal adj = adjustmentMapper.selectById(adjId);
		if (adj == null)
			throw new IllegalArgumentException("调整记录不存在");
		scopeService.requireWrite(adj.getOrgId());
		AdjustmentJournal reversal = new AdjustmentJournal();
		reversal.setId(UUID.randomUUID().toString().replace("-", ""));
		reversal.setOrgId(adj.getOrgId());
		reversal.setClosingPeriodId(adj.getClosingPeriodId());
		reversal.setAdjustmentType("REVERSAL");
		reversal.setAmount(adj.getAmount().negate());
		reversal.setReason("冲正:" + adj.getReason());
		reversal.setApprovedBy(currentUserId());
		FinJournal journal = financeService.refundCash(adj.getOrgId(), adj.getAmount(), "REV_ADJ_" + adj.getId(),
				reversal.getReason());
		reversal.setJournalId(journal.getId());
		adjustmentMapper.insert(reversal);
		return reversal;
	}

	public Page<RebateRule> pageRules(Page<RebateRule> page, String status) {
		return ruleMapper.selectPage(page,
				Wrappers.<RebateRule>lambdaQuery()
					.eq(status != null, RebateRule::getStatus, status)
					.orderByDesc(RebateRule::getCreateTime));
	}

	public Page<RebateCalculation> pageCalculations(Page<RebateCalculation> page, String orgId) {
		scopeService.requireRead(orgId);
		return calcMapper.selectPage(page,
				Wrappers.<RebateCalculation>lambdaQuery()
					.eq(RebateCalculation::getOrgId, orgId)
					.orderByDesc(RebateCalculation::getCreatedAt));
	}

	public Page<RefundCase> pageRefunds(Page<RefundCase> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return refundMapper.selectPage(page,
				Wrappers.<RefundCase>lambdaQuery()
					.eq(RefundCase::getOrgId, orgId)
					.eq(status != null, RefundCase::getStatus, status)
					.orderByDesc(RefundCase::getCreateTime));
	}

	public RefundCase getRefund(String id) {
		RefundCase refund = refundMapper.selectById(id);
		if (refund == null)
			throw new IllegalArgumentException("退款案件不存在");
		scopeService.requireRead(refund.getOrgId());
		return refund;
	}

	public Page<InvoiceApplication> pageInvoices(Page<InvoiceApplication> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return invoiceMapper.selectPage(page,
				Wrappers.<InvoiceApplication>lambdaQuery()
					.eq(InvoiceApplication::getOrgId, orgId)
					.eq(status != null, InvoiceApplication::getInvoiceStatus, status)
					.orderByDesc(InvoiceApplication::getCreateTime));
	}

	public InvoiceApplication getInvoice(String id) {
		InvoiceApplication invoice = invoiceMapper.selectById(id);
		if (invoice == null)
			throw new IllegalArgumentException("发票申请不存在");
		scopeService.requireRead(invoice.getOrgId());
		return invoice;
	}

	public Page<ReconciliationRun> pageReconciliations(Page<ReconciliationRun> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return reconMapper.selectPage(page,
				Wrappers.<ReconciliationRun>lambdaQuery()
					.eq(ReconciliationRun::getOrgId, orgId)
					.eq(status != null, ReconciliationRun::getStatus, status)
					.orderByDesc(ReconciliationRun::getCreatedAt));
	}

	public ReconciliationRun getReconciliation(String id) {
		ReconciliationRun reconciliation = reconMapper.selectById(id);
		if (reconciliation == null)
			throw new IllegalArgumentException("对账记录不存在");
		scopeService.requireRead(reconciliation.getOrgId());
		return reconciliation;
	}

	public Page<ClosingPeriod> pageClosingPeriods(Page<ClosingPeriod> page, String orgId, String status) {
		scopeService.requireRead(orgId);
		return closingMapper.selectPage(page,
				Wrappers.<ClosingPeriod>lambdaQuery()
					.eq(ClosingPeriod::getOrgId, orgId)
					.eq(status != null, ClosingPeriod::getStatus, status)
					.orderByDesc(ClosingPeriod::getCreateTime));
	}

	public ClosingPeriod getClosingPeriod(String id) {
		ClosingPeriod period = closingMapper.selectById(id);
		if (period == null)
			throw new IllegalArgumentException("关账期间不存在");
		scopeService.requireRead(period.getOrgId());
		return period;
	}

	public Page<AdjustmentJournal> pageAdjustments(Page<AdjustmentJournal> page, String orgId) {
		scopeService.requireRead(orgId);
		return adjustmentMapper.selectPage(page,
				Wrappers.<AdjustmentJournal>lambdaQuery()
					.eq(AdjustmentJournal::getOrgId, orgId)
					.orderByDesc(AdjustmentJournal::getCreatedAt));
	}

	public AdjustmentJournal getAdjustment(String id) {
		AdjustmentJournal adjustment = adjustmentMapper.selectById(id);
		if (adjustment == null)
			throw new IllegalArgumentException("调整记录不存在");
		scopeService.requireRead(adjustment.getOrgId());
		return adjustment;
	}

	// ── Helpers ──

	private RebateRule requireRule(String id) {
		RebateRule r = ruleMapper.selectById(id);
		if (r == null)
			throw new IllegalArgumentException("返点规则不存在");
		return r;
	}

	private RefundCase requireRefund(String id) {
		RefundCase c = refundMapper.selectById(id);
		if (c == null)
			throw new IllegalArgumentException("退款案件不存在");
		scopeService.requireWrite(c.getOrgId());
		return c;
	}

	private InvoiceApplication requireInvoice(String id) {
		InvoiceApplication a = invoiceMapper.selectById(id);
		if (a == null)
			throw new IllegalArgumentException("发票申请不存在");
		scopeService.requireWrite(a.getOrgId());
		return a;
	}

	private ReconciliationRun requireRecon(String id) {
		ReconciliationRun r = reconMapper.selectById(id);
		if (r == null)
			throw new IllegalArgumentException("对账记录不存在");
		scopeService.requireWrite(r.getOrgId());
		return r;
	}

	private ClosingPeriod requireClosingPeriod(String id) {
		ClosingPeriod cp = closingMapper.selectById(id);
		if (cp == null)
			throw new IllegalArgumentException("关账期间不存在");
		scopeService.requireWrite(cp.getOrgId());
		return cp;
	}

	private void requireValidPeriod(LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start))
			throw new IllegalArgumentException("账期开始和结束日期不合法");
	}

	private int ruleVersionCount(String ruleId) {
		return ruleVersionMapper
			.selectCount(Wrappers.<RebateRuleVersion>lambdaQuery().eq(RebateRuleVersion::getRuleId, ruleId))
			.intValue();
	}

	private BigDecimal getTotalReconciliationDiff(String orgId, LocalDate start, LocalDate end) {
		var runs = reconMapper.selectList(Wrappers.<ReconciliationRun>lambdaQuery()
			.eq(ReconciliationRun::getOrgId, orgId)
			.ge(ReconciliationRun::getPeriodStart, start)
			.le(ReconciliationRun::getPeriodEnd, end));
		return runs.stream()
			.map(r -> r.getDifference() != null ? r.getDifference() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private String computeInputHash(String orgId, LocalDate start, LocalDate end) {
		try {
			String input = orgId + "|" + start + "|" + end + "|Asia/Shanghai|CNY";
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			return "hash-error";
		}
	}

	private String currentUserId() {
		try {
			var u = SecurityUtils.getUser();
			return u != null ? u.getUserId() : "system";
		}
		catch (Exception e) {
			return "system";
		}
	}

}
