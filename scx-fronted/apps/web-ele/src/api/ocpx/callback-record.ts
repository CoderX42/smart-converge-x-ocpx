import { requestClient } from '#/api/request';

export type CallbackEventCode =
  | 'ACTIVATE'
  | 'AUTHENTICATION'
  | 'CUSTOMER_ACQUISITION'
  | 'EVOKE'
  | 'EVOKE_C'
  | 'EVOKE_C_VALID'
  | 'EVOKE_FIRST'
  | 'FINISH_THE_ORDER'
  | 'FIRST_ACTIVATION'
  | 'FIRST_ORDER'
  | 'HUIFANG'
  | 'LAHUI'
  | 'LAHUO'
  | 'LOGIN'
  | 'NEXT_STAY'
  | 'ORDER'
  | 'PAYMENT'
  | 'REGISTER'
  | 'VISITS';

export type CallbackRecord = {
  advertiserId: string;
  advertiserName?: string;
  billingReasonCode?: string;
  billingStatus: 'NOT_APPLICABLE' | 'POSTED' | 'REJECTED_INSUFFICIENT_BALANCE';
  currencyAmount: number;
  deductionDecidedAt?: string;
  deductionDecision: 'DEDUCTED' | 'PASSED' | 'PENDING' | null;
  deductionRatioSnapshot?: number;
  eventCode: CallbackEventCode;
  id: string;
  mediaId: string;
  mediaName?: string;
  monitorTaskId: string;
  monitorTaskName?: string;
  promotionLinkId: string;
  promotionLinkName?: string;
  receivedAt: string;
  requestId: string;
  spendAmount: number;
  targetConversion: boolean;
};

export type CallbackRecordPage = {
  current: number;
  records: CallbackRecord[];
  size: number;
  total: number;
};

export type CallbackRecordQuery = {
  advertiserId?: string;
  current?: number;
  deductionDecision?: 'DEDUCTED' | 'PASSED' | 'PENDING' | 'UNAVAILABLE';
  endDate: string;
  eventCode?: CallbackEventCode;
  mediaId?: string;
  monitorTaskId?: string;
  promotionLinkId?: string;
  requestId?: string;
  size?: number;
  startDate: string;
  targetConversion?: boolean;
};

export function getCallbackRecords(params: CallbackRecordQuery) {
  return requestClient.get<CallbackRecordPage>('/ocpx/callback-records', {
    params,
  });
}
