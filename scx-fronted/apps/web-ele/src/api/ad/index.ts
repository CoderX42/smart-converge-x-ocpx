import { requestClient } from '#/api/request';

export type PageResult<T> = { records: T[]; total: number };
export type PageQuery = {
  current: number;
  size: number;
};
export type Media = {
  code: string;
  id: string;
  name: string;
  settlementCurrency: 'CNY';
  status: 'ACTIVE' | 'DISABLED';
  timezone: string;
  version: number;
};
export type MediaScope = {
  createTime?: string;
  id: string;
  subjectId: string;
  subjectType: 'MEDIA';
  userId: string;
};
export type MediaScopeUserOption = {
  id: string;
  nickname?: string;
  username: string;
};
export type MediaQuery = PageQuery & {
  keyword?: string;
  status?: Media['status'];
};
export type MediaCreateInput = {
  code: string;
  name: string;
  settlementCurrency?: 'CNY';
  timezone?: string;
};
export type MediaUpdateInput = {
  name: string;
  settlementCurrency: 'CNY';
  timezone: string;
  version: number;
};
export type MediaStatusInput = Pick<Media, 'status' | 'version'>;
export type IntegrationDocumentType =
  'MEDIA_API' | 'MEDIA_CALLBACK' | 'OTHER' | 'PRODUCT_API' | 'RTA_SPEC';
export type IntegrationDocumentStatus =
  'ACTIVE' | 'DISABLED' | 'DRAFT' | 'RETIRED';
export type IntegrationDocumentStorageType = 'EXTERNAL' | 'OBJECT';
export type IntegrationDocument = {
  createBy?: string;
  createTime?: string;
  description?: string;
  documentVersion: number;
  externalUrl?: string;
  fileSize?: number;
  id: string;
  mediaId: string;
  mimeType?: string;
  name: string;
  originalFileName?: string;
  status: IntegrationDocumentStatus;
  storageType: IntegrationDocumentStorageType;
  type: IntegrationDocumentType;
  updateBy?: string;
  updateTime?: string;
  version: number;
};
export type IntegrationDocumentQuery = {
  current: number;
  keyword?: string;
  mediaId: string;
  size: number;
  status?: IntegrationDocumentStatus;
  storageType?: IntegrationDocumentStorageType;
  type?: IntegrationDocumentType;
};
export type IntegrationDocumentInput = {
  description?: string;
  externalUrl?: string;
  id?: string;
  mediaId: string;
  name: string;
  status: IntegrationDocumentStatus;
  storageType: IntegrationDocumentStorageType;
  type: IntegrationDocumentType;
  version?: number;
};
export type PartnerStatus = 'ACTIVE' | 'DISABLED';
export type Partner = {
  code: string;
  createTime?: string;
  description?: string;
  id: string;
  mediaId: string;
  name: string;
  status: PartnerStatus;
  type: 'UPSTREAM';
  updateTime?: string;
  version: number;
};
export type PartnerQuery = PageQuery & {
  keyword?: string;
  mediaId: string;
  status?: PartnerStatus;
};
export type PartnerInput = {
  code: string;
  description?: string;
  id?: string;
  mediaId: string;
  name: string;
  status: PartnerStatus;
  type: 'UPSTREAM';
  version?: number;
};
export type ClickSensitiveDetail = {
  attributeKeys: string[];
  campaignIdMasked?: string;
  clickIdMasked?: string;
  creativeIdMasked?: string;
  deviceModel?: string;
  ipProtected: boolean;
  oaidMd5Protected: boolean;
  oaidProtected: boolean;
  rawEventId: string;
};
export type MasterType =
  | 'integration-documents'
  | 'media-channels'
  | 'partners'
  | 'products'
  | 'upstream-customers';

export function getMedia(params?: Partial<MediaQuery>) {
  return requestClient.get<PageResult<Media>>('/subjects/media', {
    params: params ?? { current: 1, size: 100 },
  });
}
export function getManagedMedia(params?: Partial<MediaQuery>) {
  return requestClient.get<PageResult<Media>>('/subjects/media/manage', {
    params: params ?? { current: 1, size: 100 },
  });
}
export function saveMedia(data: Partial<Media>) {
  if (data.id) {
    const payload: MediaUpdateInput = {
      name: data.name || '',
      settlementCurrency: 'CNY',
      timezone: data.timezone || 'Asia/Shanghai',
      version: data.version as number,
    };
    return requestClient.put<Media>(`/subjects/media/${data.id}`, payload);
  }
  const payload: MediaCreateInput = {
    code: data.code || '',
    name: data.name || '',
    settlementCurrency: 'CNY',
    timezone: data.timezone || 'Asia/Shanghai',
  };
  return requestClient.post<Media>('/subjects/media', payload);
}
export function changeMediaStatus(id: string, data: MediaStatusInput) {
  return requestClient.put<Media>(`/subjects/media/${id}/status`, data);
}
export function deleteMedia(id: string, version: number) {
  return requestClient.delete<boolean>(`/subjects/media/${id}`, {
    params: { version },
  });
}
export function getMediaScopes(
  params: PageQuery & { mediaId: string; userId?: string },
) {
  return requestClient.get<PageResult<MediaScope>>('/subjects/media-scopes', {
    params,
  });
}
export function getMediaScopeCandidates(
  params: PageQuery & { keyword?: string; mediaId: string },
) {
  return requestClient.get<PageResult<MediaScopeUserOption>>(
    '/subjects/media-scopes/candidates',
    { params },
  );
}
export function grantMediaScope(data: { mediaId: string; userId: string }) {
  return requestClient.post<MediaScope>('/subjects/media-scopes', data);
}
export function revokeMediaScope(id: string, mediaId: string) {
  return requestClient.delete(`/subjects/media-scopes/${id}`, {
    params: { mediaId },
  });
}
export function getPartners(params: PartnerQuery) {
  return requestClient.get<PageResult<Partner>>('/ad/partners', { params });
}
export function savePartner(data: PartnerInput) {
  return data.id
    ? requestClient.put<boolean>(`/ad/partners/${data.id}`, data)
    : requestClient.post<boolean>('/ad/partners', data);
}
export function deletePartner(id: string, version: number) {
  return requestClient.delete<boolean>(`/ad/partners/${id}`, {
    params: { version },
  });
}
export function getMaster<T>(type: MasterType, params: Record<string, any>) {
  return requestClient.get<PageResult<T>>(`/ad/${type}`, { params });
}
export function getUpstreamCustomers(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/upstream-customers', {
    params,
  });
}
export function saveUpstreamCustomer(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/upstream-customers/${data.id}`, data)
    : requestClient.post('/ad/upstream-customers', data);
}
export function saveMaster(type: MasterType, data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/${type}/${data.id}`, data)
    : requestClient.post(`/ad/${type}`, data);
}
export function deleteProduct(id: string, version: number) {
  return requestClient.delete<boolean>(`/ad/products/${id}`, {
    params: { version },
  });
}
export function deleteMediaChannel(id: string, version: number) {
  return requestClient.delete<boolean>(`/ad/media-channels/${id}`, {
    params: { version },
  });
}
export function getIntegrationDocuments(params: IntegrationDocumentQuery) {
  return requestClient.get<PageResult<IntegrationDocument>>(
    '/ad/integration-documents',
    { params },
  );
}
export function saveIntegrationDocument(
  data: IntegrationDocumentInput,
  file?: File,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
) {
  const formData = new FormData();
  formData.append(
    'metadata',
    new Blob([JSON.stringify(data)], { type: 'application/json' }),
  );
  if (file) formData.append('file', file);
  const config = {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event: { loaded: number; total?: number }) => {
      if (event.total && onProgress) {
        onProgress(Math.round((event.loaded * 100) / event.total));
      }
    },
    signal,
  };
  return data.id
    ? requestClient.put<IntegrationDocument>(
        `/ad/integration-documents/${data.id}`,
        formData,
        config,
      )
    : requestClient.post<IntegrationDocument>(
        '/ad/integration-documents',
        formData,
        config,
      );
}
export function deleteIntegrationDocument(id: string, version: number) {
  return requestClient.delete<boolean>(`/ad/integration-documents/${id}`, {
    params: { version },
  });
}
export function getIntegrationDocumentContent(id: string) {
  return requestClient.download<Blob>(
    `/ad/integration-documents/${id}/content`,
  );
}
export function getTrackingLinks(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/tracking-links', { params });
}
export function saveTrackingLink(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/tracking-links/${data.id}`, data)
    : requestClient.post('/ad/tracking-links', data);
}
export function changeTrackingLinkStatus(
  id: string,
  data: Record<string, any>,
) {
  return requestClient.put(`/ad/tracking-links/${id}/status`, data);
}
export function getEventMappings(id: string) {
  return requestClient.get<any[]>(`/ad/tracking-links/${id}/event-mappings`);
}
export function saveEventMappings(id: string, data: any[]) {
  return requestClient.put(`/ad/tracking-links/${id}/event-mappings`, data);
}
export function getTrackingLinkRevisions(id: string) {
  return requestClient.get<any[]>(`/ad/tracking-links/${id}/revisions`);
}
export function getTrackingLinkStatusHistory(id: string) {
  return requestClient.get<any[]>(`/ad/tracking-links/${id}/status-history`);
}
export function getTrackingTasks(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/tracking-tasks', { params });
}
export function getSystemUsers() {
  return requestClient.get<any[]>('/system/user/list');
}
export function getTaskScopes(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/task-scopes', { params });
}
export function saveTaskScope(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/task-scopes/${data.id}`, data)
    : requestClient.post('/ad/task-scopes', data);
}
export function deleteTaskScope(id: string, mediaId: string) {
  return requestClient.delete(`/ad/task-scopes/${id}`, { params: { mediaId } });
}
export function saveTrackingTask(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/tracking-tasks/${data.id}`, data)
    : requestClient.post('/ad/tracking-tasks', data);
}
export function changeTrackingTaskStatus(
  id: string,
  data: Record<string, any>,
) {
  return requestClient.put(`/ad/tracking-tasks/${id}/status`, data);
}
export function getTrackingTaskRevisions(id: string) {
  return requestClient.get<any[]>(`/ad/tracking-tasks/${id}/revisions`);
}
export function getTrackingTaskStatusHistory(id: string) {
  return requestClient.get<any[]>(`/ad/tracking-tasks/${id}/status-history`);
}
export function getRtaEndpoints(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/rta-endpoints', { params });
}
export function saveRtaEndpoint(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/rta-endpoints/${data.id}`, data)
    : requestClient.post('/ad/rta-endpoints', data);
}
export function changeRtaEndpointStatus(id: string, data: Record<string, any>) {
  return requestClient.put(`/ad/rta-endpoints/${id}/status`, data);
}
export function checkRtaEndpoint(id: string) {
  return requestClient.post(`/ad/rta-endpoints/${id}/health-check`);
}
export function getRtaDecisionLogs(id: string, params: Record<string, any>) {
  return requestClient.get<PageResult<any>>(
    `/ad/rta-endpoints/${id}/decision-logs`,
    { params },
  );
}
export function getDailyReport(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/daily', { params });
}
export function getReportBreakdown(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/breakdowns', { params });
}
export function getCpaReport(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/cpa', { params });
}
export function getHourlyReport(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/hourly', { params });
}
export function getResultReport(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/result', { params });
}
export function getResultCbReport(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/result/cb', { params });
}
export function getAttributionResults(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/attribution-results', {
    params,
  });
}
export function getCallbackResults(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/callback-results', {
    params,
  });
}
export function getReportSyncRuns(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/sync-runs', {
    params,
  });
}
export function syncMediaReport(
  mediaAccountId: string,
  params: { end: string; start: string },
) {
  return requestClient.post<any[]>(
    `/ad/reports/media-accounts/${mediaAccountId}/sync`,
    undefined,
    { params },
  );
}
export function createReportBackfill(data: Record<string, any>) {
  return requestClient.post<any>('/ad/reports/backfills', data);
}
export function getReportBackfill(id: string) {
  return requestClient.get<any>(`/ad/reports/backfills/${id}`);
}
export function getReportOverview(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/overview', { params });
}
export function refreshDailyReport(date: string, mediaId: string) {
  return requestClient.post<any>(
    `/ad/reports/daily/${date}/refresh`,
    undefined,
    {
      params: { mediaId },
    },
  );
}
export function getEventDetails(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/details', { params });
}
export function getClickSummary(rawEventId: string, mediaId: string) {
  return requestClient.get<ClickSensitiveDetail>(
    `/ad/reports/details/${rawEventId}/click-sensitive`,
    {
      params: { mediaId },
    },
  );
}
export function createExportTask(data: Record<string, any>) {
  return requestClient.post<any>('/ad/export-tasks', data);
}
export function getExportTasks(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/export-tasks', { params });
}
export function retryExportTask(id: string) {
  return requestClient.post<any>(`/ad/export-tasks/${id}/retry`);
}
export function getCallbackJobs(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/callback-jobs', { params });
}
export function getCallbackQueueStats(mediaId: string) {
  return requestClient.get<any>('/ad/callback-jobs/stats', {
    params: { mediaId },
  });
}
export function getOutboxEvents(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/callback-jobs/outbox', {
    params,
  });
}
export type AsyncServicesQueueType = 'callback' | 'export' | 'outbox';
export type AsyncServicesQueueStats = {
  deadCount: number;
  delayedCount: number;
  failedCount: number;
  pausedCount: number;
  runningCount: number;
  succeededCount: number;
  waitingCount: number;
};
export function getAsyncServicesStats(
  queueType: AsyncServicesQueueType,
  mediaId: string,
) {
  return requestClient.get<AsyncServicesQueueStats>(
    '/system/async-services/stats',
    {
      params: { queueType, mediaId },
    },
  );
}
export function getAsyncServicesList(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/system/async-services/list', {
    params,
  });
}
export function getShortLinks(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/short-links', { params });
}
export function saveShortLink(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/short-links/${data.id}`, data)
    : requestClient.post('/ad/short-links', data);
}
export function convertDpShortLink(data: {
  linkType: 'JD_DP' | 'TAOBAO_DP';
  sourceUrl: string;
}) {
  return requestClient.post<{
    linkType: string;
    sourceUrl: string;
    targetUrl: string;
  }>('/ad/short-links/dp/convert', data);
}
export function getPlatformConfigs(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/platform-configs', { params });
}
export function savePlatformConfig(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/platform-configs/${data.id}`, data)
    : requestClient.post('/ad/platform-configs', data);
}
export function getChannelUsers(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/channel-users', { params });
}
export function saveChannelUser(data: Record<string, any>) {
  return data.id
    ? requestClient.put(`/ad/channel-users/${data.id}`, data)
    : requestClient.post('/ad/channel-users', data);
}
export function deleteChannelUser(
  id: string,
  mediaId: string,
  version: number,
) {
  return requestClient.delete<boolean>(`/ad/channel-users/${id}`, {
    params: { mediaId, version },
  });
}
export function getChannelClients(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/channel-clients', { params });
}
export function createChannelClient(data: Record<string, any>) {
  return requestClient.post<Record<string, any>>('/ad/channel-clients', data);
}
export function updateChannelClient(id: string, data: Record<string, any>) {
  return requestClient.put(`/ad/channel-clients/${id}`, data);
}
export function getChannelCredentials(id: string, mediaId: string) {
  return requestClient.get<Record<string, any>[]>(
    `/ad/channel-clients/${id}/credentials`,
    { params: { mediaId } },
  );
}
export function rotateChannelCredential(id: string, mediaId: string) {
  return requestClient.post<Record<string, any>>(
    `/ad/channel-clients/${id}/credentials/rotate`,
    undefined,
    { params: { mediaId } },
  );
}
export function revokeChannelCredential(
  id: string,
  keyVersion: string,
  mediaId: string,
) {
  return requestClient.post(
    `/ad/channel-clients/${id}/credentials/${keyVersion}/revoke`,
    undefined,
    { params: { mediaId } },
  );
}
export function getAttributionReviews(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/attributions', { params });
}
export function reviewAttribution(
  id: string,
  mediaId: string,
  data: Record<string, any>,
) {
  return requestClient.post(`/ad/attributions/${id}/review`, data, {
    params: { mediaId },
  });
}
export function dispatchCallbackJobs(mediaId: string) {
  return requestClient.post<number>('/ad/callback-jobs/dispatch', undefined, {
    params: { mediaId },
  });
}
export function dispatchCallbackJob(id: string, mediaId: string) {
  return requestClient.post(`/ad/callback-jobs/${id}/dispatch`, undefined, {
    params: { mediaId },
  });
}
export function dispatchOutboxEvent(id: string, mediaId: string) {
  return requestClient.post(
    `/ad/callback-jobs/outbox/${id}/dispatch`,
    undefined,
    {
      params: { mediaId },
    },
  );
}
export function replayCallbackJob(id: string, mediaId: string) {
  return requestClient.post(`/ad/callback-jobs/${id}/replay`, undefined, {
    params: { mediaId },
  });
}
export function pauseCallbackJob(id: string, mediaId: string) {
  return requestClient.post(`/ad/callback-jobs/${id}/pause`, undefined, {
    params: { mediaId },
  });
}
export function resumeCallbackJob(id: string, mediaId: string) {
  return requestClient.post(`/ad/callback-jobs/${id}/resume`, undefined, {
    params: { mediaId },
  });
}
