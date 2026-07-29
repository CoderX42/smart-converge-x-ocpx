import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type FinancialAccount = BaseRecord & {
  accountType: string;
  availableBalance: number;
  balance: number;
  currency: 'CNY';
  frozenBalance: number;
  orgId: string;
};

export type Journal = BaseRecord & {
  amount: number;
  businessType: string;
  idempotencyKey: string;
  orgId: string;
};

export type CreditLine = BaseRecord & {
  creditLimit: number;
  orgId: string;
  usedAmount: number;
};

export type BudgetReservation = BaseRecord & {
  amount: number;
  consumedAmount: number;
  orgId: string;
  referenceId?: string;
};

export type Refund = BaseRecord & {
  amount: number;
  checkerId?: string;
  makerId?: string;
  orgId: string;
  refundReason?: string;
};

export type Rebate = BaseRecord & {
  orgId: string;
  periodEnd?: string;
  periodStart?: string;
  rebateAmount?: number;
  ruleId?: string;
};

export type Invoice = BaseRecord & {
  invoiceAmount: number;
  invoiceNo?: string;
  invoiceStatus: string;
  orgId: string;
};

export type Reconciliation = BaseRecord & {
  difference?: number;
  orgId: string;
  periodEnd: string;
  periodStart: string;
};

export type ClosingPeriod = BaseRecord & {
  orgId: string;
  periodEnd: string;
  periodStart: string;
};

export function getFinancialAccounts(
  params: PageQuery & { orgId?: string },
) {
  return requestClient.get<PageResult<FinancialAccount>>(
    '/ad/finance/accounts/page',
    { params },
  );
}

export function getJournals(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<Journal>>('/ad/finance/journals/page', {
    params,
  });
}

export function getCreditLines(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<CreditLine>>(
    '/ad/finance/credit-lines',
    { params },
  );
}

export function getBudgetReservations(
  params: PageQuery & { orgId?: string },
) {
  return requestClient.get<PageResult<BudgetReservation>>(
    '/ad/finance/budget-reservations/page',
    { params },
  );
}

export function recharge(params: {
  amount: number;
  idempotencyKey: string;
  orgId: string;
  remark?: string;
}) {
  return requestClient.post('/ad/finance/recharge', undefined, { params });
}

export function getRefunds(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<Refund>>('/ad/finance/flows/refunds', {
    params,
  });
}

export function createRefund(params: {
  amount: number;
  orgId: string;
  reason: string;
}) {
  return requestClient.post('/ad/finance/flows/refunds', undefined, { params });
}

export function submitRefund(id: string) {
  return requestClient.post(`/ad/finance/flows/refunds/${id}/submit`);
}

export function reviewRefund(
  id: string,
  decision: 'APPROVE' | 'REJECT',
) {
  return requestClient.post(
    `/ad/finance/flows/refunds/${id}/review`,
    undefined,
    { params: { decision } },
  );
}

export function getRebates(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<Rebate>>(
    '/ad/finance/flows/rebate-calculations',
    { params },
  );
}

export function calculateRebate(params: {
  end: string;
  orgId: string;
  ruleId: string;
  start: string;
}) {
  return requestClient.post(
    '/ad/finance/flows/rebate-calculations',
    undefined,
    { params },
  );
}

export function getInvoices(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<Invoice>>('/ad/finance/flows/invoices', {
    params,
  });
}

export function createInvoice(params: { amount: number; orgId: string }) {
  return requestClient.post('/ad/finance/flows/invoices', undefined, {
    params,
  });
}

export function issueInvoice(id: string) {
  return requestClient.post(`/ad/finance/flows/invoices/${id}/issue`);
}

export function getReconciliations(
  params: PageQuery & { orgId?: string },
) {
  return requestClient.get<PageResult<Reconciliation>>(
    '/ad/finance/flows/reconciliations',
    { params },
  );
}

export function createReconciliation(params: {
  end: string;
  orgId: string;
  start: string;
}) {
  return requestClient.post('/ad/finance/flows/reconciliations', undefined, {
    params,
  });
}

export function getClosingPeriods(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<ClosingPeriod>>(
    '/ad/finance/flows/closing-periods',
    { params },
  );
}

export function createClosingPeriod(params: {
  end: string;
  orgId: string;
  start: string;
}) {
  return requestClient.post('/ad/finance/flows/closing-periods', undefined, {
    params,
  });
}

export function closePeriod(id: string, maxDiffAllowed: number) {
  return requestClient.post(
    `/ad/finance/flows/closing-periods/${id}/close`,
    undefined,
    { params: { maxDiffAllowed } },
  );
}
