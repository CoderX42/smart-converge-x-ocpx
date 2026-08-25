import { requestClient } from '#/api/request';

export * from './ocpx/advertiser-macros';
export * from './ocpx/billing';
export * from './ocpx/callback-record';
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
  stageCode: string;
  traceId?: string;
};

export type FlowLogPage = {
  current: number;
  hasNext: boolean;
  records: FlowLog[];
  size: number;
  total: number;
};

export type FlowLogOption = {
  id: string;
  label: string;
};

export type FlowLogOptions = {
  advertisers: FlowLogOption[];
  media: FlowLogOption[];
  monitorTasks: FlowLogOption[];
  promotionLinks: FlowLogOption[];
};

export type FlowLogQuery = {
  advertiserId?: string;
  current: number;
  endDate: string;
  eventId?: string;
  flowType?: string;
  mediaId?: string;
  monitorTaskId?: string;
  promotionLinkId?: string;
  resultStatus?: string;
  size: number;
  stageCode?: string;
  startDate: string;
};

export type FlowLogRequestDetail = {
  aaid?: string;
  advertiserId?: string;
  androidId?: string;
  androidIdMd5?: string;
  caid?: string;
  caid1?: string;
  caid2?: string;
  caidList?: string;
  callbackUrl?: string;
  clickId?: string;
  coverageStatus:
    'DETAIL_EXPIRED' | 'HISTORICAL_INCOMPLETE' | 'TRACE_AVAILABLE';
  creativeId?: string;
  deviceOs?: string;
  durationMs: number;
  eventCode: string;
  eventId: string;
  eventTime: string;
  externalAdvertiserId?: string;
  feedbackLogId?: number;
  flowCreatedAt: string;
  flowLogId: string;
  flowType: string;
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
  mediaId?: string;
  message?: string;
  model?: string;
  monitorTaskId?: string;
  oaid?: string;
  oaidMd5?: string;
  osVersion?: string;
  partnerHttpStatus?: number;
  planId?: string;
  previousCaid?: string;
  promotionLinkId?: string;
  receivedAt?: string;
  requestId?: string;
  responsePreview?: string;
  resultStatus: 'FAILED' | 'SUCCESS';
  sourceEventId?: string;
  stages: FlowLogStage[];
  timestamp?: string;
  timestampMs?: string;
  traceId?: string;
  ua?: string;
  userId?: string;
  userType?: string;
};

export type FlowLogStage = {
  durationMs: number;
  eventCode: string;
  eventTime: string;
  flowType: string;
  id: string;
  macroAudit: Record<string, unknown>;
  message?: string;
  partnerHttpStatus?: number;
  requestEndpoint?: string;
  requestMethod?: string;
  requestParams: Record<string, unknown>;
  requestTruncated: boolean;
  responsePreview?: string;
  resultStatus: 'FAILED' | 'SUCCESS';
  stageCode: string;
};

export type MediaMacro = {
  attributionIdentity: boolean;
  group: string;
  key: string;
  label: string;
  macro: string;
  osSupport: 'ANDROID' | 'BOTH' | 'IOS';
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

export function getFlowLogs(params: FlowLogQuery) {
  return requestClient.get<FlowLogPage>('/ocpx/flow-logs', { params });
}

export function getFlowLogOptions(params?: {
  advertiserId?: string;
  mediaId?: string;
}) {
  return requestClient.get<FlowLogOptions>('/ocpx/flow-logs/options', {
    params,
  });
}

export function getFlowLogRequestDetail(id: string) {
  return requestClient.get<FlowLogRequestDetail>(
    `/ocpx/flow-logs/${id}/request-detail`,
  );
}

export function getMediaMacros() {
  return requestClient.get<MediaMacro[]>('/ocpx/media-macros');
}
