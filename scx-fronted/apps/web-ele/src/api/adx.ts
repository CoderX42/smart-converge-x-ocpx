import type { PageResult } from './business';

import { requestClient } from '#/api/request';

export type CatalogRecord = {
  createTime?: string;
  id: string;
  updateTime?: string;
};

export type AdapterDefinition = {
  adapterCode: string;
  adapterName: string;
  capabilitiesJson: string;
  direction: 'DOWNSTREAM' | 'UPSTREAM';
  status: string;
};

export type OcpxIntegration = {
  authType: string;
  callbackPathPattern: string;
  downstreamAdapterCode: string;
  feedbackMethod: string;
  feedbackUrl: string;
  keyVersion: number;
  monitorId: string;
  monitorName: string;
  postmanCollectionPath: string;
  requestExample: string;
  requiredHeaders: string[];
  routeStatus: string;
  schemaVersion: string;
  secretConfigured: boolean;
  signatureFormula: string;
  upstreamAdapterCode: string;
};

export type Channel = CatalogRecord & {
  adapterCode: string;
  authType: string;
  callbackHostAllowlist: string;
  channelCode: string;
  channelName: string;
  configVersion: number;
  keyVersion: number;
  macroParams: string;
  secretConfigured: boolean;
  secretKey?: string;
  status: string;
  uri: string;
};

export type Advertiser = CatalogRecord & {
  account: string;
  adapterCode: string;
  advertiserName: string;
  balance: number;
  bidUrl: string;
  configVersion: number;
  macroParams?: string;
  password?: string;
  secretConfigured: boolean;
  secretKey?: string;
  status: string;
  upstreamHostAllowlist: string;
};

export type AccountEntry = {
  advertiserId: string;
  amount?: number;
  balanceAfter?: number;
  balanceBefore?: number;
  createTime: string;
  currency?: string;
  id: string;
  mode: string;
  operatorId?: string;
  reason?: string;
  remark?: string;
  status: string;
};

export type AppLink = CatalogRecord & {
  advertiserId: string;
  advertiserName?: string;
  deeplink?: string;
  linkChannel?: string;
  linkExt?: string;
  linkName: string;
  linkPlatform: number;
  linkRemark?: string;
  linkUrl: string;
};

export type MonitorLink = CatalogRecord & {
  advertiserId: string;
  advertiserName?: string;
  appLinkId: string;
  appLinkName?: string;
  billingMode: string;
  bizId?: string;
  channelId: string;
  channelName?: string;
  clickDedupWindowSeconds: number;
  clickUrl?: string;
  configVersion: number;
  downstreamCallbackUrl?: string;
  effectiveUpstreamFeedbackUrl?: string;
  monitorName: string;
  publishedAt?: string;
  remark?: string;
  routeStatus: string;
  rtaMode: string;
  showUrl?: string;
  unitPrice: number;
  upstreamFeedbackSource?: string;
  upstreamFeedbackUrl?: string;
};

export type DeductionRule = CatalogRecord & {
  appLinkId: string;
  appLinkName?: string;
  channelId: string;
  channelName?: string;
  configVersion: number;
  ratio: number;
};

export type ReportRow = {
  acquisitionCount: number;
  activateCount: number;
  activateRate?: number;
  advertiserId: string;
  advertiserName: string;
  appLinkId: string;
  appLinkName: string;
  authenticationCount: number;
  channelId: string;
  channelName: string;
  classifiedClickCount: number;
  clickRate: number;
  clicks: number;
  costAmount: number;
  deductedConversionCount: number;
  deductionRatio: number;
  deduplicatedClickCount: number;
  deliveryFailureCount: number;
  eligibleClickCount: number;
  evokeCount: number;
  finishOrderCount: number;
  firstActivateCount: number;
  firstActivateRate?: number;
  firstOrderCount: number;
  forwardedConversionCount: number;
  impressions: number;
  loginCount: number;
  monitorId: string;
  monitorName: string;
  nextStayCount: number;
  orderCount: number;
  paymentCount: number;
  rawConversionCount: number;
  reactivationCount: number;
  reactivationRate?: number;
  registerCount: number;
  retention3dCount: number;
  retention4dCount: number;
  retention5dCount: number;
  retention6dCount: number;
  retention7dCount: number;
  revenueAmount: number;
  skippedClickCount: number;
  timeBucket: string;
  visitCount: number;
};

export type RuntimeOverview = {
  byDirection: Record<string, number>;
  deadDeliveries: number;
  feedbackStreamPending: number;
  ingressDlq: number;
  pendingDeliveries: number;
  retryDeliveries: number;
  successfulDeliveries: number;
};

export type RuntimeDelivery = {
  adapterCode: string;
  attemptCount: number;
  createTime: string;
  direction: string;
  eventId: string;
  id: string;
  lastError?: string;
  nextRetryAt: string;
  sourceEventId: string;
  status: string;
  updateTime: string;
};

export type RuntimeIngressDlq = {
  createTime: string;
  eventId?: string;
  id: string;
  partitionNo: number;
  reason: string;
  replayedAt?: string;
  status: string;
  streamRecordId: string;
};

export type RuntimeEvent = {
  advertiserId: string;
  advertiserName?: string;
  amount?: number;
  amountRaw?: string;
  channelId: string;
  channelName?: string;
  direction: string;
  eventId: string;
  eventType: string;
  id: string;
  monitorId: string;
  monitorName?: string;
  occurredAt: string;
  parentEventId?: string;
  receivedAt: string;
  status: string;
};

export type RuntimeTimelineItem = {
  detail?: string;
  occurredAt: string;
  referenceId: string;
  stage: string;
  status: string;
};

export type RuntimeDeliveryAttempt = {
  attemptNo: number;
  createTime: string;
  durationMs?: number;
  errorMessage?: string;
  httpStatus?: number;
  id: string;
  jobId: string;
  requestMethod?: string;
  requestSummary?: string;
  responseExcerpt?: string;
  status: string;
  targetHost?: string;
  targetPath?: string;
};

export type RuntimeIngressAudit = {
  channelCode?: string;
  clientId?: string;
  createTime: string;
  durationMs?: number;
  eventId?: string;
  httpStatus: number;
  id: string;
  keyVersion?: string;
  payloadHash?: string;
  reason?: string;
  remoteIpMasked?: string;
  requestId?: string;
  resultStatus: string;
  routeReference?: string;
};

export type DashboardMetricSet = {
  activationRate?: number;
  activations: number;
  classifiedClicks: number;
  clicks: number;
  costAmount: number;
  deductedConversions: number;
  deduplicatedClicks: number;
  deliveryFailures: number;
  eligibleClicks: number;
  firstActivationRate?: number;
  firstActivations: number;
  forwardedConversions: number;
  impressions: number;
  payments: number;
  rawConversions: number;
  reactivationRate?: number;
  reactivations: number;
  registrations: number;
  retentionD2: number;
  retentionD3: number;
  retentionD4: number;
  retentionD5: number;
  retentionD6: number;
  retentionD7: number;
  skippedClicks: number;
};

export type DashboardComparisonMetric = {
  changeRate?: number;
  comparisonLabel: string;
  comparisonValue: number;
  value: number;
};

export type DashboardSummary = {
  configuration: {
    abnormalRoutes: number;
    activeAdvertisers: number;
    activeAppLinks: number;
    activeChannels: number;
    draftRoutes: number;
    publishedRoutes: number;
  };
  dataCoverage: {
    classifiedClicks: number;
    clickQualityComplete: boolean;
    rateStatus: 'COMPLETE' | 'INCOMPLETE_HISTORY' | 'NO_ELIGIBLE_CLICK';
    retentionBasis: 'UPSTREAM_REPORTED_EVENT';
    totalClicks: number;
  };
  dataUpdatedAt?: string;
  filters: {
    advertiserId?: string;
    appLinkId?: string;
    channelId?: string;
    endDate: string;
    monitorId?: string;
    startDate: string;
  };
  funnel: Array<{
    key: string;
    label: string;
    previousRate?: number;
    value: number;
  }>;
  generatedAt: string;
  highlights: {
    todayActivations: DashboardComparisonMetric;
    todayClicks: DashboardComparisonMetric;
    todayFirstActivations: DashboardComparisonMetric;
    todayPayments: DashboardComparisonMetric;
    yesterdayRetentionD2: DashboardComparisonMetric;
  };
  retention: Array<{ day: string; reportDate: string; value: number }>;
  timezone: string;
  totals: DashboardMetricSet;
  trend: Array<{ metrics: DashboardMetricSet; reportDate: string }>;
};

export type DashboardAccountSummary = {
  cpcSpend: number;
  currency: string;
  generatedAt: string;
  rechargeAmount: number;
  rejectedCharges: number;
  totalBalance: number;
};

export type ReportOptions = {
  advertisers: Advertiser[];
  appLinks: AppLink[];
  channels: Channel[];
  monitors: MonitorLink[];
};

export type ApiDebugTarget = {
  advertiserId: string;
  advertiserName: string;
  billingMode: string;
  channelCode: string;
  channelId: string;
  channelName: string;
  downstreamAdapterCode: string;
  downstreamTargetHost?: string;
  monitorId: string;
  monitorName: string;
  routeStatus: string;
  routeVersion: number;
  unitPrice: number;
  upstreamAdapterCode: string;
  upstreamTargetHost?: string;
};

export type ApiDebugRequest = {
  body?: string;
  bodyType: 'FORM_DATA' | 'FORM_URLENCODED' | 'JSON' | 'NONE' | 'TEXT';
  headers: Record<string, string>;
  method: 'DELETE' | 'GET' | 'HEAD' | 'OPTIONS' | 'PATCH' | 'POST' | 'PUT';
  mode: 'CUSTOM' | 'INTEGRATED';
  monitorId?: string;
  scenario?: 'DOWNSTREAM_TO_UPSTREAM' | 'UPSTREAM_TO_DOWNSTREAM';
  timeoutSeconds: number;
  url: string;
};

export type ApiDebugPreflight = {
  billable: boolean;
  confirmationPhrase: string;
  draftId: string;
  environment: string;
  expiresAt: string;
  riskSummary: string;
  sideEffectSummary: string;
  target?: ApiDebugTarget;
};

export type ApiDebugExecution = {
  createdBy: string;
  createTime: string;
  durationMs?: number;
  errorMessage?: string;
  id: string;
  mode: string;
  monitorId?: string;
  requestBody?: string;
  requestHeaders?: string;
  requestMethod: string;
  requestUrl: string;
  responseBody?: string;
  responseHeaders?: string;
  responseMime?: string;
  responseSha256?: string;
  responseSize?: number;
  responseStatus?: number;
  riskSummary?: string;
  routeVersion?: number;
  scenario?: string;
  sideEffectSummary?: string;
  status: string;
  updateTime: string;
};

export type ApiDebugExecutionLog = {
  createTime: string;
  detailJson?: string;
  level: string;
  message: string;
  phase: string;
  sequence: number;
  source: 'ENGINE' | 'MANAGEMENT';
};

export type ApiDebugCase = {
  caseName: string;
  createdBy: string;
  createTime: string;
  description?: string;
  id: string;
  mode: 'CUSTOM' | 'INTEGRATED';
  monitorId?: string;
  requestJson: string;
  scenario?: 'DOWNSTREAM_TO_UPSTREAM' | 'UPSTREAM_TO_DOWNSTREAM';
  updatedBy: string;
  updateTime: string;
};

export type ApiDebugRecentLog = {
  level: string;
  logger: string;
  message: string;
  sequence: number;
  source: 'ENGINE' | 'MANAGEMENT';
  timestamp: string;
};

export type ApiDebugRecentLogs = {
  nextCursor: number;
  records: ApiDebugRecentLog[];
  truncated: boolean;
};

type Query = {
  [key: string]: any;
  current?: number;
  keyword?: string;
  size?: number;
};

const endpoints = {
  advertiser: '/adx/advertisers',
  appLink: '/adx/app-links',
  channel: '/adx/channels',
  deduction: '/adx/deduction-rules',
  monitor: '/adx/monitors',
} as const;

export type CatalogKind = keyof typeof endpoints;

type KindRecord<K extends CatalogKind> = K extends 'channel'
  ? Channel
  : K extends 'advertiser'
    ? Advertiser
    : K extends 'appLink'
      ? AppLink
      : K extends 'monitor'
        ? MonitorLink
        : DeductionRule;

export function getCatalog<K extends CatalogKind>(kind: K, params: Query) {
  return requestClient.get<PageResult<KindRecord<K>>>(endpoints[kind], {
    params,
  });
}

export function getAdapters() {
  return requestClient.get<AdapterDefinition[]>('/adx/adapters');
}

export function getMonitorIntegration(id: string) {
  return requestClient.get<OcpxIntegration>(`/adx/monitors/${id}/integration`);
}

export function saveCatalog<K extends CatalogKind>(
  kind: K,
  data: Partial<KindRecord<K>>,
) {
  return data.id
    ? requestClient.put(`${endpoints[kind]}/${data.id}`, data)
    : requestClient.post(endpoints[kind], data);
}

export function deleteCatalog(kind: CatalogKind, id: string) {
  return requestClient.delete<boolean>(`${endpoints[kind]}/${id}`);
}

export function rechargeAdvertiser(
  id: string,
  amount: number,
  requestId: string,
) {
  return requestClient.post(`/adx/advertisers/${id}/recharge`, {
    amount,
    requestId,
  });
}

export function getAdvertiserLedger(id: string, params: Query) {
  return requestClient.get<PageResult<AccountEntry>>(
    `/adx/advertisers/${id}/ledger`,
    { params },
  );
}

export function publishMonitor(id: string) {
  return requestClient.post(`/adx/monitors/${id}/publish`);
}

export function getRuntimeOverview() {
  return requestClient.get<RuntimeOverview>('/adx/runtime/overview');
}

export function getRuntimeDeliveries(params: Query) {
  return requestClient.get<PageResult<RuntimeDelivery>>(
    '/adx/runtime/deliveries',
    { params },
  );
}

export function getRuntimeEvents(params: Query) {
  return requestClient.get<PageResult<RuntimeEvent>>('/adx/runtime/events', {
    params,
  });
}

export function getRuntimeEventTimeline(id: string) {
  return requestClient.get<RuntimeTimelineItem[]>(
    `/adx/runtime/events/${id}/timeline`,
  );
}

export function getRuntimeDeliveryAttempts(id: string) {
  return requestClient.get<RuntimeDeliveryAttempt[]>(
    `/adx/runtime/deliveries/${id}/attempts`,
  );
}

export function getRuntimeIngressAudits(params: Query) {
  return requestClient.get<PageResult<RuntimeIngressAudit>>(
    '/adx/runtime/ingress-audits',
    { params },
  );
}

export function replayRuntimeDelivery(id: string) {
  return requestClient.post(`/adx/runtime/deliveries/${id}/replay`);
}

export function getRuntimeIngressDlq(params: Query) {
  return requestClient.get<PageResult<RuntimeIngressDlq>>(
    '/adx/runtime/ingress-dlq',
    { params },
  );
}

export function replayRuntimeIngressDlq(id: string) {
  return requestClient.post(`/adx/runtime/ingress-dlq/${id}/replay`);
}

export function getReport(params: Query) {
  return requestClient.get<PageResult<ReportRow>>('/adx/reports/hourly', {
    params,
  });
}

export function getReportOptions() {
  return requestClient.get<ReportOptions>('/adx/report-options');
}

export function exportReport(params: Query) {
  return requestClient.get<Blob>('/adx/reports/hourly/export', {
    params,
    responseReturn: 'body',
    responseType: 'blob',
  });
}

export function getDashboardSummary(params?: Query) {
  return requestClient.get<DashboardSummary>('/adx/dashboard/summary', {
    params,
  });
}

export function getDashboardAccountSummary(params?: Query) {
  return requestClient.get<DashboardAccountSummary>(
    '/adx/dashboard/account-summary',
    { params },
  );
}

export function getApiDebugTargets(keyword?: string) {
  return requestClient.get<ApiDebugTarget[]>('/adx/api-debug/targets', {
    params: { keyword },
  });
}

export function preflightApiDebug(data: ApiDebugRequest) {
  return requestClient.post<ApiDebugPreflight>(
    '/adx/api-debug/preflight',
    data,
  );
}

export function authorizeApiDebug(data: {
  confirmationPhrase: string;
  draftId: string;
  password: string;
  purpose: 'EXECUTE' | 'RAW_LOGS';
}) {
  return requestClient.post<{ expiresAt: string; token: string }>(
    '/adx/api-debug/authorizations',
    data,
  );
}

export function executeApiDebug(draftId: string, authorizationToken: string) {
  return requestClient.post<ApiDebugExecution>('/adx/api-debug/executions', {
    authorizationToken,
    draftId,
  });
}

export function executeMultipartApiDebug(
  draftId: string,
  authorizationToken: string,
  files: File[],
) {
  const data = new FormData();
  data.append('draftId', draftId);
  data.append('authorizationToken', authorizationToken);
  for (const file of files) data.append('files', file);
  return requestClient.post<ApiDebugExecution>(
    '/adx/api-debug/executions/multipart',
    data,
  );
}

export function getApiDebugExecution(id: string) {
  return requestClient.get<ApiDebugExecution>(
    `/adx/api-debug/executions/${id}`,
  );
}

export function getApiDebugExecutions(params: Query) {
  return requestClient.get<PageResult<ApiDebugExecution>>(
    '/adx/api-debug/executions',
    { params },
  );
}

export function getApiDebugExecutionLogs(id: string, afterSequence = 0) {
  return requestClient.get<ApiDebugExecutionLog[]>(
    `/adx/api-debug/executions/${id}/logs`,
    { params: { afterSequence } },
  );
}

export function authorizeApiDebugLogs(password: string) {
  return authorizeApiDebug({
    confirmationPhrase: 'VIEW RAW LOGS',
    draftId: 'RAW_LOGS',
    password,
    purpose: 'RAW_LOGS',
  });
}

export function getApiDebugRecentLogs(
  authorizationToken: string,
  params: Query,
) {
  return requestClient.get<ApiDebugRecentLogs>('/adx/api-debug/logs/recent', {
    headers: { 'X-SCX-Debug-Authorization': authorizationToken },
    params,
  });
}

export function getApiDebugCases() {
  return requestClient.get<ApiDebugCase[]>('/adx/api-debug/cases');
}

export function saveApiDebugCase(data: Partial<ApiDebugCase>) {
  return data.id
    ? requestClient.put<ApiDebugCase>(`/adx/api-debug/cases/${data.id}`, data)
    : requestClient.post<ApiDebugCase>('/adx/api-debug/cases', data);
}

export function deleteApiDebugCase(id: string) {
  return requestClient.delete<boolean>(`/adx/api-debug/cases/${id}`);
}
