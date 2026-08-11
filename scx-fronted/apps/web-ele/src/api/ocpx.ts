import type { PageResult } from './business';

import { requestClient } from '#/api/request';

export * from './ocpx/app-link';
export * from './ocpx/event-model';
export * from './ocpx/media-account';
export * from './ocpx/product';
export * from './ocpx/runtime';
export * from './ocpx/task';

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

export type AccessScopeType =
  'ADVERTISER' | 'ALL' | 'CHANNEL' | 'MEDIA_ACCOUNT';

export type AccessPermissionLevel = 'MANAGE' | 'OPERATE' | 'VIEW';

export type AccessScope = {
  id?: string;
  permissionLevel: AccessPermissionLevel;
  scopeId?: string;
  scopeName?: string;
  scopeType: AccessScopeType;
  status?: string;
  validFrom?: string;
  validTo?: string;
};

export type UserAccessScopes = {
  scopes: AccessScope[];
  userId: string;
  version: number;
};

export type AccessScopeOption = {
  id: string;
  name: string;
  scopeType: AccessScopeType;
  status: string;
};

export type AccessScopeOptions = {
  advertisers: AccessScopeOption[];
  allManageable: boolean;
  channels: AccessScopeOption[];
  mediaAccounts: AccessScopeOption[];
};

export type AccessScopeImpact = {
  advertiserCount: number;
  appLinkCount: number;
  channelCount: number;
  mediaAccountCount: number;
  monitorCount: number;
  publishedMonitorCount: number;
  userId: string;
  version: number;
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
  adapterKey?: string;
  adapterVersion: number;
  advertiserName: string;
  balance: number;
  balanceStatus: 'AVAILABLE' | 'EMPTY';
  code: string;
  configVersion: number;
  credentialConfigured: boolean;
  credentialRevision: number;
  documentRevision: number;
  integrationConfigured: boolean;
  maskedBidUrl?: string;
  republishRequired: boolean;
  status: 'ACTIVE' | 'DISABLED';
};

export type AdvertiserDetail = CatalogRecord & {
  accountMasked: string;
  adapterKey?: string;
  adapterVersion: number;
  advertiserName: string;
  balance: number;
  bidUrl?: string;
  code: string;
  configVersion: number;
  credentialConfigured: boolean;
  credentialRevision: number;
  currentDocumentId?: string;
  documentRevision: number;
  integrationConfigured: boolean;
  macroParams?: string;
  republishRequiredCount: number;
  status: 'ACTIVE' | 'DISABLED';
  upstreamHostAllowlist?: string;
};

export type AdvertiserAdapterCapability = {
  adapterKey: string;
  adapterName: string;
  authTypes: string[];
  eventVocabulary: string[];
  maxTimeoutMs?: number;
  requiresBidUrl: boolean;
  supportsCallback: boolean;
  version: number;
};

export type AdvertiserDocument = {
  contentType: string;
  createdAt: string;
  createdBy: string;
  current: boolean;
  documentType: string;
  fileName: string;
  fileSize: number;
  id: string;
  scanStatus: 'CLEAN' | 'REJECTED';
  status: 'ACTIVE' | 'RETIRED';
  summary?: string;
  version: number;
};

export type AdvertiserCreateInput = {
  account: string;
  code: string;
  extraMenuIds: string[];
  integration?: {
    adapterKey: string;
    bidUrl?: string;
    credentialInput?: string;
    macroParams?: string;
    upstreamHostAllowlist?: string;
  };
  name: string;
  password: string;
  roleIds: string[];
};

export type AdvertiserUpdateInput = {
  adapterKey?: string;
  bidUrl?: string;
  expectedVersion: number;
  macroParams?: string;
  name: string;
  upstreamHostAllowlist?: string;
};

export type AdvertiserAuthorizationRole = {
  code: string;
  id: string;
  menuIds: string[];
  name: string;
};

export type AdvertiserAuthorizationMenu = {
  children: AdvertiserAuthorizationMenu[];
  grantable: boolean;
  id: string;
  name: string;
  parentId?: string;
  permission?: string;
  type: string;
};

export type AdvertiserAuthorizationOptions = {
  mandatoryRole: AdvertiserAuthorizationRole;
  menuTree: AdvertiserAuthorizationMenu[];
  sharedRoles: AdvertiserAuthorizationRole[];
};

export type AdvertiserAuthorizationDetail = {
  accountMasked: string;
  advertiserId: string;
  effectiveMenuIds: string[];
  extraMenuIds: string[];
  grantVersion: number;
  inheritedMenuIds: string[];
  mandatoryRole: AdvertiserAuthorizationRole;
  roleIds: string[];
  userId: string;
};

export type OcpxBusinessCodeResource =
  'ADVERTISER' | 'CHANNEL' | 'EVENT' | 'PRODUCT';

export type AccountEntry = {
  advertiserId: string;
  amount?: number;
  appLinkId?: string;
  balanceAfter?: number;
  balanceBefore?: number;
  channelId?: string;
  chargeEventId: string;
  conversionEventCode?: string;
  createTime: string;
  currency?: string;
  id: string;
  mediaEventCode?: string;
  mode: string;
  monitorId?: string;
  operatorId?: string;
  reason?: string;
  remark?: string;
  sourceEventCode?: string;
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
  bindingCount: number;
  bindingSummary?: string;
  bizId?: string;
  channelId: string;
  channelName?: string;
  clickDedupWindowSeconds: number;
  clickUrl?: string;
  configVersion: number;
  downstreamCallbackUrl?: string;
  effectiveUpstreamFeedbackUrl?: string;
  eventBindings: MonitorEventBinding[];
  monitorName: string;
  publishedAt?: string;
  remark?: string;
  republishReason?: string;
  republishRequired: boolean;
  routeStatus: string;
  rtaMode: string;
  showUrl?: string;
  unitPrice: number;
  upstreamFeedbackSource?: string;
  upstreamFeedbackUrl?: string;
};

export type EventDefinition = CatalogRecord & {
  builtIn: boolean;
  category: 'CONVERSION' | 'MEDIA' | 'UPSTREAM';
  channelId?: string;
  channelName?: string;
  draftReferences: number;
  eventCode: string;
  eventName: string;
  publishedReferences: number;
  referenced: boolean;
  remark?: string;
  sortOrder: number;
  status: 'ACTIVE' | 'DISABLED';
};

export type EventMapping = CatalogRecord & {
  channelId: string;
  channelName: string;
  conversionEventCode: string;
  conversionEventId: string;
  conversionEventName: string;
  draftReferences: number;
  mediaEventCode: string;
  mediaEventId: string;
  mediaEventName: string;
  publishedReferences: number;
  remark?: string;
  status: 'ACTIVE' | 'DISABLED';
  upstreamEventCode: string;
  upstreamEventId: string;
  upstreamEventName: string;
};

export type EventOption = {
  bindingDisplayName: string;
  conversionEventCode: string;
  conversionEventName: string;
  eventMappingId: string;
  mediaEventCode: string;
  mediaEventName: string;
  upstreamEventCode: string;
  upstreamEventName: string;
};

export type MonitorEventBinding = EventOption & {
  assessmentUnitPrice: number;
  dailyLimit: number;
  id?: string;
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

export type UnifiedReportMetricCode =
  | 'activationRate'
  | 'activations'
  | 'adjustmentCount'
  | 'costAmount'
  | 'deductionCount'
  | 'duplicateClicks'
  | 'impressions'
  | 'orders'
  | 'paymentRate'
  | 'payments'
  | 'rawClicks'
  | 'registrationPaymentRate'
  | 'registrationRate'
  | 'registrations'
  | 'validClicks';

export type UnifiedReportQuery = {
  advertiserId?: string;
  appLinkId?: string;
  channelId?: string;
  current?: number;
  direction?: 'ASC' | 'DESC';
  endDate: string;
  grain: 'DAY' | 'HOUR';
  groupBy: 'LINK' | 'MEDIA' | 'PRODUCT' | 'TASK' | 'TIME';
  mediaAccountId?: string;
  mediaLevel?: 'ACCOUNT' | 'CHANNEL';
  metrics?: UnifiedReportMetricCode[];
  productId?: string;
  size?: number;
  sort?: string;
  startDate: string;
  taskId?: string;
};

export type UnifiedReportRecord = {
  asOf?: string;
  dimensionKey: string;
  dimensions: Record<string, unknown>;
  dimensionType: string;
  finalityStatus: 'DELAYED' | 'FINAL' | 'PROVISIONAL';
  metrics: Partial<Record<UnifiedReportMetricCode, UnifiedReportMetricValue>>;
  qualityStatus: 'COMPLETE' | 'PARTIAL' | 'UNMAPPED';
};

export type UnifiedReportPage = {
  asOf?: string;
  current: number;
  freshnessStatus: 'DELAYED' | 'FINAL' | 'PROVISIONAL';
  metricDefinitions: Array<{
    code: string;
    denominator?: string;
    kind: string;
    label: string;
    numerator?: string;
    sensitive: boolean;
    status: 'AVAILABLE' | 'UNAVAILABLE';
  }>;
  metricDefinitionsVersion: string;
  pageSummary: {
    label: string;
    metrics: Record<string, UnifiedReportMetricValue>;
  };
  querySummary: {
    label: string;
    metrics: Record<string, UnifiedReportMetricValue>;
  };
  records: UnifiedReportRecord[];
  size: number;
  timezone: string;
  total: number;
};

export type UnifiedReportMetricValue = {
  denominator?: number;
  numerator?: number;
  status: 'AVAILABLE' | 'FORBIDDEN' | 'UNAVAILABLE' | 'ZERO_DENOMINATOR';
  value?: number;
};

export type ReportExportTask = {
  createTime: string;
  errorCode?: string;
  expiresAt?: string;
  id: string;
  status:
    | 'CANCELLED'
    | 'EXPIRED'
    | 'FAILED'
    | 'PENDING'
    | 'PROCESSING'
    | 'RETRY_WAIT'
    | 'SUCCEEDED';
  type: 'DEDUCTION' | 'STANDARD';
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
  cpaRejectedCharges: number;
  cpaSpend: number;
  cpcSpend: number;
  currency: string;
  generatedAt: string;
  rechargeAmount: number;
  rejectedCharges: number;
  totalBalance: number;
};

export type DashboardSectionStatus = 'EMPTY' | 'ERROR' | 'READY' | 'TIMEOUT';

export type DashboardSection<T> = {
  data?: T;
  errorCode?: string;
  status: DashboardSectionStatus;
  traceId?: string;
};

export type DashboardMetricCard = {
  changeRate?: number;
  comparison?: UnifiedReportMetricValue;
  comparisonLabel: string;
  current: UnifiedReportMetricValue;
  label: string;
  metric: UnifiedReportMetricCode;
};

export type DashboardTrendPoint = {
  asOf?: string;
  bucket: string;
  finalityStatus: 'DELAYED' | 'FINAL' | 'PROVISIONAL';
  metrics: Partial<Record<UnifiedReportMetricCode, UnifiedReportMetricValue>>;
};

export type DashboardRanking = {
  groupBy: 'MEDIA' | 'PRODUCT' | 'TASK';
  records: Array<{
    asOf?: string;
    dimensionKey: string;
    finalityStatus: 'DELAYED' | 'FINAL' | 'PROVISIONAL';
    metrics: Partial<Record<UnifiedReportMetricCode, UnifiedReportMetricValue>>;
    objectId: string;
  }>;
};

export type DashboardOverview = {
  asOf?: string;
  businessDate: string;
  cacheHit: boolean;
  cards: DashboardSection<DashboardMetricCard[]>;
  endDate: string;
  freshnessStatus: 'DELAYED' | 'FINAL' | 'PROVISIONAL';
  mediaRanking: DashboardSection<DashboardRanking>;
  metricDefinitions: UnifiedReportPage['metricDefinitions'];
  metricDefinitionsVersion: string;
  partialFailure: boolean;
  productRanking: DashboardSection<DashboardRanking>;
  scopeRevision: number;
  startDate: string;
  taskRanking: DashboardSection<DashboardRanking>;
  timezone: string;
  trend: DashboardSection<DashboardTrendPoint[]>;
};

export type DashboardQuery = {
  advertiserId?: string;
  appLinkId?: string;
  channelId?: string;
  endDate: string;
  mediaAccountId?: string;
  productId?: string;
  startDate: string;
  taskId?: string;
};

export type ReportOptions = {
  advertisers: AdvertiserOption[];
  appLinks: AppLink[];
  channels: Channel[];
  monitors: MonitorLink[];
};

export type UnifiedReportOptions = {
  advertisers: Array<{ id: string; label: string; status: string }>;
  appLinks: Array<{ id: string; label: string; status: string }>;
  channels: Array<{ id: string; label: string; status: string }>;
  mediaAccounts: Array<{ id: string; label: string; status: string }>;
  products: Array<{ id: string; label: string; status: string }>;
  tasks: Array<{ id: string; label: string; status: string }>;
};

export type AdvertiserOption = {
  advertiserName: string;
  id: string;
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
  advertiser: '/ocpx/advertisers',
  appLink: '/ocpx/app-links',
  channel: '/ocpx/channels',
  deduction: '/ocpx/deduction-rules',
  monitor: '/ocpx/monitors',
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
  return requestClient.get<AdapterDefinition[]>('/ocpx/adapters');
}

export function getMonitorIntegration(id: string) {
  return requestClient.get<OcpxIntegration>(`/ocpx/monitors/${id}/integration`);
}

export function getMonitor(id: string) {
  return requestClient.get<MonitorLink>(`/ocpx/monitors/${id}`);
}

export function getEventDefinitions(params: Query) {
  return requestClient.get<PageResult<EventDefinition>>('/ocpx/events', {
    params,
  });
}

export function saveEventDefinition(data: Partial<EventDefinition>) {
  const { id, ...payload } = data;
  return id
    ? requestClient.put(`/ocpx/events/${id}`, payload)
    : requestClient.post('/ocpx/events', payload);
}

export function disableEventDefinition(id: string) {
  return requestClient.delete<boolean>(`/ocpx/events/${id}`);
}

export function getEventMappings(params: Query) {
  return requestClient.get<PageResult<EventMapping>>('/ocpx/event-mappings', {
    params,
  });
}

export function saveEventMapping(data: Partial<EventMapping>) {
  const { id, ...payload } = data;
  return id
    ? requestClient.put(`/ocpx/event-mappings/${id}`, payload)
    : requestClient.post('/ocpx/event-mappings', payload);
}

export function disableEventMapping(id: string) {
  return requestClient.delete<boolean>(`/ocpx/event-mappings/${id}`);
}

export function getChannelEventOptions(channelId: string) {
  return requestClient.get<EventOption[]>(
    `/ocpx/channels/${channelId}/event-options`,
  );
}

export function saveCatalog<K extends CatalogKind>(
  kind: K,
  data: Partial<KindRecord<K>>,
) {
  if (kind === 'advertiser') {
    throw new Error('Advertiser writes must use the revision-aware API');
  }
  return data.id
    ? requestClient.put(`${endpoints[kind]}/${data.id}`, data)
    : requestClient.post(endpoints[kind], data);
}

export function deleteCatalog(kind: CatalogKind, id: string) {
  if (kind === 'advertiser') {
    throw new Error('Advertisers are disabled by status change, not deleted');
  }
  return requestClient.delete<boolean>(`${endpoints[kind]}/${id}`);
}

export function getAdvertisers(params: Query) {
  return requestClient.get<PageResult<Advertiser>>('/ocpx/advertisers', {
    params,
  });
}

export function getAdvertiserDetail(id: string) {
  return requestClient.get<AdvertiserDetail>(`/ocpx/advertisers/${id}`);
}

export function getAdvertiserAdapterCapabilities() {
  return requestClient.get<AdvertiserAdapterCapability[]>(
    '/ocpx/advertisers/adapter-capabilities',
  );
}

export function getAdvertiserAuthorizationOptions() {
  return requestClient.get<AdvertiserAuthorizationOptions>(
    '/ocpx/advertisers/login-authorization/options',
  );
}

export function getAdvertiserAuthorization(id: string) {
  return requestClient.get<AdvertiserAuthorizationDetail>(
    `/ocpx/advertisers/${id}/login-authorization`,
  );
}

export function updateAdvertiserAuthorization(
  id: string,
  data: {
    expectedGrantVersion: number;
    extraMenuIds: string[];
    roleIds: string[];
  },
) {
  return requestClient.put<AdvertiserAuthorizationDetail>(
    `/ocpx/advertisers/${id}/login-authorization`,
    data,
  );
}

export function generateOcpxBusinessCode(
  resourceType: OcpxBusinessCodeResource,
) {
  return requestClient.post<{
    code: string;
    resourceType: OcpxBusinessCodeResource;
  }>('/ocpx/business-codes/generate', { resourceType });
}

export function createAdvertiser(
  data: AdvertiserCreateInput,
  idempotencyKey: string,
) {
  return requestClient.post<AdvertiserDetail>('/ocpx/advertisers', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateAdvertiser(id: string, data: AdvertiserUpdateInput) {
  return requestClient.put<AdvertiserDetail>(`/ocpx/advertisers/${id}`, data);
}

export function secondaryAuthenticate(password: string) {
  return requestClient.post('/secondary-auth', { password });
}

export function rotateAdvertiserCredential(
  id: string,
  data: {
    credentialInput: string;
    expectedCredentialRevision: number;
    reason: string;
  },
) {
  return requestClient.post<{
    configured: boolean;
    overlapUntil?: string;
    republishRequiredCount: number;
    revision: number;
  }>(`/ocpx/advertisers/${id}/credentials:rotate`, data);
}

export function changeAdvertiserStatus(
  id: string,
  data: {
    confirmed: boolean;
    expectedVersion: number;
    reason: string;
    status: 'ACTIVE' | 'DISABLED';
  },
) {
  return requestClient.post(`/ocpx/advertisers/${id}/status`, data);
}

export function getAdvertiserDocuments(id: string) {
  return requestClient.get<AdvertiserDocument[]>(
    `/ocpx/advertisers/${id}/documents`,
  );
}

export function uploadAdvertiserDocument(
  id: string,
  documentType: string,
  summary: string,
  file: File,
) {
  const data = new FormData();
  data.append('documentType', documentType);
  if (summary) data.append('summary', summary);
  data.append('file', file);
  return requestClient.post<AdvertiserDocument>(
    `/ocpx/advertisers/${id}/documents`,
    data,
  );
}

export function setCurrentAdvertiserDocument(
  advertiserId: string,
  documentId: string,
  expectedVersion: number,
) {
  return requestClient.post(
    `/ocpx/advertisers/${advertiserId}/documents/${documentId}:current`,
    { expectedVersion },
  );
}

export function retireAdvertiserDocument(
  advertiserId: string,
  documentId: string,
  reason: string,
) {
  return requestClient.post(
    `/ocpx/advertisers/${advertiserId}/documents/${documentId}:retire`,
    { reason },
  );
}

export function downloadAdvertiserDocument(
  advertiserId: string,
  documentId: string,
) {
  return requestClient.get<Blob>(
    `/ocpx/advertisers/${advertiserId}/documents/${documentId}/content`,
    { responseReturn: 'body', responseType: 'blob' },
  );
}

export function rechargeAdvertiser(
  id: string,
  amount: number,
  requestId: string,
) {
  return requestClient.post(`/ocpx/advertisers/${id}/recharge`, {
    amount,
    requestId,
  });
}

export function getAdvertiserLedger(id: string, params: Query) {
  return requestClient.get<PageResult<AccountEntry>>(
    `/ocpx/advertisers/${id}/ledger`,
    { params },
  );
}

export function publishMonitor(id: string) {
  return requestClient.post(`/ocpx/monitors/${id}/publish`);
}

export function getReport(params: UnifiedReportQuery) {
  return requestClient.get<UnifiedReportPage>('/ocpx/reports/hourly', {
    params,
  });
}

export function getReportOptions() {
  return requestClient.get<ReportOptions>('/ocpx/report-options');
}

export function getUnifiedReportOptions() {
  return requestClient.get<UnifiedReportOptions>('/ocpx/reports/options');
}

export function exportReport(data: UnifiedReportQuery) {
  return requestClient.post<ReportExportTask>('/ocpx/reports/exports', data);
}

export function exportDeductionReport(data: UnifiedReportQuery) {
  return requestClient.post<ReportExportTask>(
    '/ocpx/reports/deduction-exports',
    data,
  );
}

export function getReportExport(id: string) {
  return requestClient.get<ReportExportTask>(`/ocpx/reports/exports/${id}`);
}

export function cancelReportExport(id: string) {
  return requestClient.post<ReportExportTask>(
    `/ocpx/reports/exports/${id}/cancel`,
  );
}

export function downloadReportExport(id: string) {
  return requestClient.get<Blob>(`/ocpx/reports/exports/${id}/download`, {
    responseReturn: 'body',
    responseType: 'blob',
  });
}

export function getDashboardSummary(params: DashboardQuery) {
  return requestClient.get<DashboardOverview>('/ocpx/dashboard/summary', {
    params,
  });
}

export function getDashboardOptions() {
  return requestClient.get<UnifiedReportOptions>('/ocpx/dashboard/options');
}

export function getDashboardAccountSummary(params?: Query) {
  return requestClient.get<DashboardAccountSummary>(
    '/ocpx/dashboard/account-summary',
    { params },
  );
}

export function getApiDebugTargets(keyword?: string) {
  return requestClient.get<ApiDebugTarget[]>('/ocpx/api-debug/targets', {
    params: { keyword },
  });
}

export function preflightApiDebug(data: ApiDebugRequest) {
  return requestClient.post<ApiDebugPreflight>(
    '/ocpx/api-debug/preflight',
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
    '/ocpx/api-debug/authorizations',
    data,
  );
}

export function executeApiDebug(draftId: string, authorizationToken: string) {
  return requestClient.post<ApiDebugExecution>('/ocpx/api-debug/executions', {
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
    '/ocpx/api-debug/executions/multipart',
    data,
  );
}

export function getApiDebugExecution(id: string) {
  return requestClient.get<ApiDebugExecution>(
    `/ocpx/api-debug/executions/${id}`,
  );
}

export function getApiDebugExecutions(params: Query) {
  return requestClient.get<PageResult<ApiDebugExecution>>(
    '/ocpx/api-debug/executions',
    { params },
  );
}

export function getApiDebugExecutionLogs(id: string, afterSequence = 0) {
  return requestClient.get<ApiDebugExecutionLog[]>(
    `/ocpx/api-debug/executions/${id}/logs`,
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
  return requestClient.get<ApiDebugRecentLogs>('/ocpx/api-debug/logs/recent', {
    headers: { 'X-SCX-Debug-Authorization': authorizationToken },
    params,
  });
}

export function getApiDebugCases() {
  return requestClient.get<ApiDebugCase[]>('/ocpx/api-debug/cases');
}

export function saveApiDebugCase(data: Partial<ApiDebugCase>) {
  return data.id
    ? requestClient.put<ApiDebugCase>(`/ocpx/api-debug/cases/${data.id}`, data)
    : requestClient.post<ApiDebugCase>('/ocpx/api-debug/cases', data);
}

export function deleteApiDebugCase(id: string) {
  return requestClient.delete<boolean>(`/ocpx/api-debug/cases/${id}`);
}

export function getUserAccessScopes(userId: string) {
  return requestClient.get<UserAccessScopes>(
    `/ocpx/access-scopes/users/${userId}`,
  );
}

export function getAccessScopeOptions() {
  return requestClient.get<AccessScopeOptions>('/ocpx/access-scopes/options');
}

export function getUserAccessScopeImpact(userId: string) {
  return requestClient.get<AccessScopeImpact>(
    `/ocpx/access-scopes/users/${userId}/impact`,
  );
}

export function replaceUserAccessScopes(
  userId: string,
  version: number,
  scopes: AccessScope[],
) {
  return requestClient.put<UserAccessScopes>(
    `/ocpx/access-scopes/users/${userId}`,
    { scopes, version },
  );
}
