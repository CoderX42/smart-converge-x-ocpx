import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type BillingEntryType =
  'CLICK_CHARGE' | 'CONVERSION_CHARGE' | 'MANUAL_DEBIT' | 'RECHARGE';

export type BillingEntry = {
  advertiserId: string;
  advertiserName: string;
  amount: number;
  balanceAfter: number;
  balanceBefore: number;
  businessKey: string;
  entryType: BillingEntryType;
  eventFactId?: string;
  id: string;
  monitorTaskId?: string;
  monitorTaskName?: string;
  occurredAt: string;
  operatorUserId?: string;
  reasonCode?: string;
  remark?: string;
  status: 'POSTED' | 'REJECTED';
};

export function listBillingEntries(params: Record<string, unknown>) {
  return requestClient.get<PageResult<BillingEntry>>('/ocpx/billing-entries', {
    params,
  });
}
