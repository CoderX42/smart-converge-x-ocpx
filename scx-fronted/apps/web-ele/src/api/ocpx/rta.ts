import { requestClient } from '#/api/request';

export type RtaKind =
  'bindings' | 'logs' | 'media' | 'records' | 'reports' | 'upstreams';
export type RtaProtocol = {
  bindingFields: Array<{ key: string; label: string; pattern: string }>;
  code: string;
  configFields: Array<{ key: string; label: string; pattern: string }>;
  credentialRequired: boolean;
  fixedEndpoint: string;
  maxCandidates: number;
  mode: string;
  name: string;
  retrySafe: boolean;
};
export function rtaProtocols() {
  return requestClient.get<RtaProtocol[]>('/ocpx/rta/protocols');
}
export type RtaRow = {
  accessCode?: string;
  admissionSeconds?: number;
  advertiserId?: string;
  advertiserName?: string;
  allowCount?: number;
  allowRate?: number;
  callbackUrl?: string;
  completedAt?: number;
  credentialRef?: string;
  deadline?: number;
  decisionMode?: string;
  deliveryAttempts?: number;
  deliveryFailedCount?: number;
  deliveryStatus?: string;
  deliverySuccessCount?: number;
  denyCount?: number;
  dispatchStatus?: string;
  durationMs?: number;
  enabled?: boolean | number;
  endpoint?: string;
  errorCount?: number;
  eventTime?: string;
  expiresAt?: number;
  externalTaskId?: string;
  id?: string;
  matchedTaskIds?: string[];
  mediaId?: string;
  mediaName?: string;
  monitorTaskId?: string;
  name?: string;
  notifyUrl?: string;
  pendingCount?: number;
  productId?: string;
  protocol?: string;
  protocolConfig?: Record<string, string>;
  providerRequestId?: string;
  reason?: string;
  reasonCode?: string;
  receivedAt?: number;
  relayRequestId?: string;
  requestCount?: number;
  requestId?: string;
  requestUrl?: string;
  stage?: string;
  status?: string;
  taskId?: string;
  taskName?: string;
  timeHour?: string;
  timeoutCount?: number;
  timeoutMs?: number;
  updateTime?: string;
  upstreamId?: string;
  version?: number;
};
export type RtaPage = {
  current: number;
  dataAsOf?: string;
  records: RtaRow[];
  size: number;
  total: number;
};
export function queryRta(
  kind: RtaKind,
  params: Record<string, number | string>,
) {
  return requestClient.get<RtaPage>(`/ocpx/rta/${kind}`, { params });
}
export function saveRta(
  kind: 'bindings' | 'media' | 'upstreams',
  data: RtaRow,
) {
  const allowedFields: Record<
    'bindings' | 'media' | 'upstreams',
    Array<keyof RtaRow>
  > = {
    bindings: [
      'monitorTaskId',
      'upstreamId',
      'productId',
      'externalTaskId',
      'enabled',
      'version',
    ],
    media: ['mediaId', 'notifyUrl', 'credentialRef', 'enabled', 'version'],
    upstreams: [
      'id',
      'advertiserId',
      'protocol',
      'protocolConfig',
      'name',
      'accessCode',
      'endpoint',
      'credentialRef',
      'timeoutMs',
      'admissionSeconds',
      'enabled',
      'version',
    ],
  };
  const fields = allowedFields[kind];
  const body = Object.fromEntries(
    fields
      .filter((key) => data[key] !== undefined)
      .map((key) => [key, data[key]]),
  );
  return requestClient.post(`/ocpx/rta/${kind}`, body);
}

export function rtaOptions(
  kind: 'advertisers' | 'media' | 'tasks' | 'upstreams',
  params: Record<string, string> = {},
) {
  return requestClient.get<
    Array<{ id: string; name: string; protocol?: string }>
  >(`/ocpx/rta/options/${kind}`, { params });
}
