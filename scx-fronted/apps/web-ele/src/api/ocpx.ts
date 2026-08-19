import { requestClient } from '#/api/request';

export * from './ocpx/advertiser-macros';
export * from './ocpx/billing';
export * from './ocpx/creative';
export * from './ocpx/deduction';
export * from './ocpx/four-entity';
export * from './ocpx/monitor-task-integration';
export * from './ocpx/monitor-task-test';
export * from './ocpx/report';

export type ReportOption = {
  id: string;
  label: string;
};

export type UnifiedReportOptions = {
  advertisers: ReportOption[];
  media: ReportOption[];
  monitorTasks: ReportOption[];
  promotionLinks: ReportOption[];
};

export type UnifiedReportGroupBy =
  'ADVERTISER' | 'MEDIA' | 'MONITOR_TASK' | 'PROMOTION_LINK' | 'TIME';

export type MetricValue = { amount: number; count: number };

export type UnifiedReportRecord = {
  dimensionKey: string;
  dimensionName: string;
  dimensionType: UnifiedReportGroupBy;
  metrics: Record<string, MetricValue>;
};

export type UnifiedReportQuery = {
  advertiserId?: string;
  current?: number;
  endDate: string;
  groupBy: UnifiedReportGroupBy;
  mediaId?: string;
  monitorTaskId?: string;
  promotionLinkId?: string;
  size?: number;
  startDate: string;
};

export type UnifiedReportPage = {
  asOf: string;
  current: number;
  records: UnifiedReportRecord[];
  size: number;
  summary: Record<string, MetricValue>;
  total: number;
};

export type DashboardMetric = {
  status: 'OK' | 'ZERO_DENOMINATOR';
  unit: 'CNY' | 'COUNT' | 'PERCENT';
  value: null | number;
};

export type DashboardAccount = {
  advertiserId: string;
  availableBalance: number;
  rejectedClickCount: number;
  rejectedConversionCount: number;
  todaySpend: number;
};

export type DashboardOverview = {
  account?: DashboardAccount;
  asOf?: string;
  businessDate: string;
  dataStatus: 'EMPTY' | 'READY';
  endDate: string;
  metrics: Record<string, DashboardMetric>;
  startDate: string;
  timezone: string;
};

export type FlowLog = {
  advertiserId: string;
  advertiserName: string;
  createTime: string;
  durationMs: number;
  eventCode: string;
  eventId: string;
  eventTime: string;
  flowType: 'CALLBACK' | 'FEEDBACK';
  id: string;
  mediaId: string;
  mediaName: string;
  message?: string;
  monitorTaskId: string;
  monitorTaskName: string;
  partnerHttpStatus?: number;
  promotionLinkId: string;
  promotionLinkName: string;
  resultStatus: 'FAILED' | 'SUCCESS';
  sourceEventId?: string;
};

export type FlowLogPage = {
  current: number;
  records: FlowLog[];
  size: number;
  total: number;
};

export type FlowLogRequestDetail = {
  aaid?: string;
  advertiserId: string;
  androidId?: string;
  androidIdMd5?: string;
  caid?: string;
  caid1?: string;
  caid2?: string;
  caidList?: string;
  callbackUrl?: string;
  clickId?: string;
  creativeId?: string;
  deviceOs?: string;
  externalAdvertiserId?: string;
  feedbackLogId: number;
  groupId?: string;
  idfa?: string;
  idfaMd5?: string;
  imei?: string;
  imeiMd5?: string;
  impressionId?: string;
  ip?: string;
  ipv6?: string;
  lastCaid?: string;
  mac?: string;
  mediaId: string;
  model?: string;
  monitorTaskId: string;
  oaid?: string;
  oaidMd5?: string;
  osVersion?: string;
  planId?: string;
  previousCaid?: string;
  promotionLinkId: string;
  receivedAt: string;
  requestId: string;
  timestamp?: string;
  timestampMs?: string;
  ua?: string;
  userId?: string;
  userType?: string;
};

export function getReport(params: UnifiedReportQuery) {
  return requestClient.get<UnifiedReportPage>('/ocpx/reports/hourly', {
    params,
  });
}

export function getUnifiedReportOptions() {
  return requestClient.get<UnifiedReportOptions>('/ocpx/reports/options');
}

export function getDashboardSummary(
  params: Partial<Omit<UnifiedReportQuery, 'groupBy'>>,
) {
  return requestClient.get<DashboardOverview>('/ocpx/dashboard/summary', {
    params,
  });
}

export function getDashboardOptions() {
  return requestClient.get<UnifiedReportOptions>('/ocpx/dashboard/options');
}

export function getFlowLogs(params: Record<string, unknown>) {
  return requestClient.get<FlowLogPage>('/ocpx/flow-logs', { params });
}

export function getFlowLogRequestDetail(id: string) {
  return requestClient.get<FlowLogRequestDetail>(
    `/ocpx/flow-logs/${id}/request-detail`,
  );
}
