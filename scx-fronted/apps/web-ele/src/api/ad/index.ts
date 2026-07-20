import { requestClient } from '#/api/request';

export type PageResult<T> = { records: T[]; total: number };
export type Media = {
  code: string;
  id: string;
  name: string;
  timezone: string;
};
export type MasterType =
  | 'integration-documents'
  | 'media-channels'
  | 'partners'
  | 'products'
  | 'upstream-customers';

export function getMedia(params?: Record<string, any>) {
  return requestClient.get<PageResult<Media>>('/subjects/media', {
    params: params ?? { current: 1, size: 100 },
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
export function uploadIntegrationDocument(data: FormData) {
  return requestClient.post<string>('/ad/integration-documents/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
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
export function getReportOverview(params: Record<string, any>) {
  return requestClient.get<any[]>('/ad/reports/overview', { params });
}
export function refreshDailyReport(date: string, mediaId: string) {
  return requestClient.post(`/ad/reports/daily/${date}/refresh`, undefined, {
    params: { mediaId },
  });
}
export function getEventDetails(params: Record<string, any>) {
  return requestClient.get<PageResult<any>>('/ad/reports/details', { params });
}
export function getMaskedClickDetail(rawEventId: string, mediaId: string) {
  return requestClient.get<any>(
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
