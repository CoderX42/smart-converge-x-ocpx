import { requestClient } from '#/api/request';
import { downloadBlobFile } from '#/utils/util';

export type SummaryReportRow = {
  activate: number;
  advertiserId: string;
  advertiserName: string;
  authentication: number;
  basePrice: number;
  callbackCount: number;
  clickRate: null | number;
  currencyAmount: number;
  customerAcquisition: number;
  deductionDeductedCount: number;
  deductionPassedCount: number;
  deductionPendingCount: number;
  evoke: number;
  finishTheOrder: number;
  firstActivation: number;
  firstOrder: number;
  linkRadio: null | string;
  login: number;
  mediaId: string;
  mediaName: string;
  monitorName: string;
  monitorTaskId: string;
  nextStay: number;
  payment: number;
  placeOrder: number;
  promotionLinkId: string;
  promotionLinkName: string;
  register: number;
  reportTime: string;
  uniClkSmt: number;
  uniShowSmt: number;
  visits: number;
};

export type SummaryReportPage = {
  current: number;
  deductionCoverageStart: null | string;
  deductionCoverageStatus: 'FULL' | 'NONE' | 'PARTIAL';
  records: SummaryReportRow[];
  size: number;
  total: number;
};

export type SummaryReportQuery = {
  advertiserId?: string;
  asc?: string;
  current?: number;
  dateType?: number;
  desc?: string;
  endTime: string;
  mediaId?: string;
  monitorTaskId?: string;
  promotionLinkId?: string;
  size?: number;
  startTime: string;
};

export function getSummaryReport(params: SummaryReportQuery) {
  return requestClient.get<SummaryReportPage>('/ocpx/reports/summary', {
    params,
  });
}

export function exportSummaryReport(
  params: Omit<SummaryReportQuery, 'asc' | 'current' | 'desc' | 'size'>,
) {
  return downloadBlobFile(
    '/ocpx/reports/summary:export',
    params,
    '时段报表.csv',
  );
}
