import { requestClient } from '#/api/request';

export type RtaProfile = {
  adapterCode: 'MOCK';
  code: string;
  configJson: string;
  createTime: string;
  id: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  updateTime: string;
  version: number;
};

export type RtaProfilePage = {
  current: number;
  records: RtaProfile[];
  size: number;
  total: number;
};

export type RtaProfilePayload = {
  adapterCode: 'MOCK';
  code: string;
  configJson: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
};

export type RtaBinding = {
  enabled: boolean;
  externalProductId?: string;
  externalTaskId: string;
  id: string;
  monitorTaskId: string;
  profileId: string;
  profileName: string;
};

export type RtaTaskConfig = {
  bindings: RtaBinding[];
  enabled: boolean;
  monitorTaskId: string;
  profileId?: null | string;
};

export type RtaTaskConfigPayload = {
  bindings: Array<{
    externalProductId?: string;
    externalTaskId: string;
  }>;
  enabled: boolean;
  profileId?: null | string;
};

export type RtaDecision = {
  advertiserId: string;
  decision: 'ALLOW' | 'DENY';
  latencyMs: number;
  mediaId: string;
  monitorTaskId: string;
  outcomeClass: 'ERROR' | 'REJECTED' | 'SUCCESS' | 'TIMEOUT';
  profileId?: null | string;
  profileName?: string;
  promotionLinkId: string;
  reasonCode: string;
  receivedAt: string;
  requestId: string;
};

export type RtaDecisionPage = {
  current: number;
  records: RtaDecision[];
  size: number;
  total: number;
};

export type RtaMetric = {
  advertiserId: string;
  allowCount: number;
  allowRate: null | number;
  bucketStart: string;
  denyCount: number;
  errorCount: number;
  mediaId: string;
  monitorTaskId: string;
  profileId: null | string;
  profileName?: string;
  promotionLinkId: string;
  requestCount: number;
  timeoutCount: number;
};

export type RtaMetricPage = {
  dataAsOf?: string;
  records: RtaMetric[];
};

export type RtaQuery = {
  advertiserId?: string;
  current?: number;
  endDate?: string;
  grain?: 'DAY' | 'HOUR';
  mediaId?: string;
  monitorTaskId?: string;
  profileId?: string;
  promotionLinkId?: string;
  size?: number;
  startDate?: string;
};

export function listRtaProfiles(params?: {
  current?: number;
  keyword?: string;
  size?: number;
}) {
  return requestClient.get<RtaProfilePage>('/ocpx/rta/profiles', { params });
}

export function createRtaProfile(data: RtaProfilePayload) {
  return requestClient.post<RtaProfile>('/ocpx/rta/profiles', data);
}

export function updateRtaProfile(id: string, data: RtaProfilePayload) {
  return requestClient.put<RtaProfile>(`/ocpx/rta/profiles/${id}`, data);
}

export function getRtaTaskConfig(taskId: string) {
  return requestClient.get<RtaTaskConfig>(`/ocpx/rta/monitor-tasks/${taskId}`);
}

export function updateRtaTaskConfig(
  taskId: string,
  data: RtaTaskConfigPayload,
) {
  return requestClient.put<RtaTaskConfig>(
    `/ocpx/rta/monitor-tasks/${taskId}`,
    data,
  );
}

export function listRtaDecisions(params: RtaQuery) {
  return requestClient.get<RtaDecisionPage>('/ocpx/rta/decisions', { params });
}

export function listRtaMetrics(params: RtaQuery) {
  return requestClient.get<RtaMetricPage>('/ocpx/rta/metrics', { params });
}
