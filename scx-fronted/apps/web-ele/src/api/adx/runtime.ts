import { requestClient } from '#/api/request';

export type RuntimeFreshness = 'DELAYED' | 'FINAL' | 'PROVISIONAL';

export type RuntimeQuery = {
  advertiserId?: string;
  appLinkId?: string;
  channelId?: string;
  current?: number;
  direction?: string;
  endTime?: string;
  eventCode?: string;
  exactId?: string;
  factType?: string;
  failureClass?: string;
  mediaAccountId?: string;
  productId?: string;
  routeVersionId?: string;
  size?: number;
  sort?: 'RECEIVED_ASC' | 'RECEIVED_DESC';
  startTime?: string;
  status?: string;
  taskId?: string;
  traceId?: string;
};

export type RuntimePage<T> = {
  asOf: string;
  current: number;
  freshnessStatus: RuntimeFreshness;
  qualityFlags: string[];
  records: T[];
  size: number;
  total: number;
  traceId: string;
};

export type RuntimeOverview = {
  accepted: number;
  asOf: string;
  byFactType: Record<string, number>;
  byFailureClass: Record<string, number>;
  dlq: number;
  failed: number;
  freshnessStatus: RuntimeFreshness;
  replayQueued: number;
  replayTerminal: number;
  replayWorkerStatus: 'DISABLED' | 'ENABLED';
  succeeded: number;
  traceId: string;
};

export type RuntimeFact = {
  acceptanceStatus: string;
  advertiserId?: string;
  appLinkId?: string;
  channelId?: string;
  correlationIdMasked?: string;
  dimensionStatus: 'FROZEN' | 'UNMAPPED';
  direction: string;
  eventCode?: string;
  eventTime: string;
  externalClickIdMasked?: string;
  factType: string;
  hasBody: false;
  id: string;
  mediaAccountId?: string;
  mediaTaskIdMasked?: string;
  operatorUserId?: string;
  processingStatus: string;
  productId?: string;
  qualityStatus: 'COMPLETE' | 'PARTIAL' | 'UNMAPPED';
  receivedTime: string;
  requestIdMasked?: string;
  routeId?: string;
  routeVersion?: number;
  routeVersionId?: string;
  summaryCode: string;
  taskId?: string;
  traceId?: string;
};

export type RuntimeFailure = {
  advertiserId?: string;
  channelId?: string;
  dimensionStatus: 'FROZEN' | 'UNMAPPED';
  errorCode: string;
  eventTime: string;
  failureClass: string;
  failureRevision: number;
  hasBody: false;
  id: string;
  mediaAccountId?: string;
  receivedTime: string;
  replayable: boolean;
  routeId?: string;
  routeVersion?: number;
  routeVersionId?: string;
  sourceFactId?: string;
  sourceType: string;
  summaryFingerprint: string;
  taskId?: string;
  traceId?: string;
};

export type RuntimeAttempt = {
  attemptNo: number;
  errorClass?: string;
  errorCode?: string;
  finishedAt?: string;
  id: string;
  nextRetryAt?: string;
  startedAt: string;
  status: string;
  summaryFingerprint?: string;
};

export type RuntimeReplayStatus = {
  attemptCount: number;
  commandId: string;
  completedAt?: string;
  createTime: string;
  failureId: string;
  lastErrorClass?: string;
  lastErrorCode?: string;
  leaseUntil?: string;
  maxAttempts: number;
  mode: 'ORIGINAL_SNAPSHOT_ONLY';
  nextAttemptAt: string;
  requestId: string;
  routeVersion: number;
  routeVersionId: string;
  status: string;
  taskId: string;
  workerStatus: 'DISABLED' | 'ENABLED';
};

export type RuntimeTimeline = {
  attempts: RuntimeAttempt[];
  executionId: string;
  fact?: RuntimeFact;
  failure?: RuntimeFailure;
  replays: RuntimeReplayStatus[];
  traceId: string;
};

export type RuntimeReplayPreview = {
  billingEffect: string;
  blockers: string[];
  errorCode: string;
  expiresAt: string;
  externalEffect: string;
  failureClass: string;
  failureId: string;
  failureRevision: number;
  mode: 'ORIGINAL_SNAPSHOT_ONLY';
  previewToken: string;
  replayable: boolean;
  routeVersion: number;
  routeVersionId: string;
  taskId: string;
  traceId: string;
  workerStatus: 'DISABLED' | 'ENABLED';
};

export function getRuntimeOverview() {
  return requestClient.get<RuntimeOverview>('/adx/runtime/overview');
}

export function getRuntimeFacts(params: RuntimeQuery) {
  return requestClient.get<RuntimePage<RuntimeFact>>('/adx/runtime/facts', {
    params,
  });
}

export function getRuntimeClicks(params: RuntimeQuery) {
  return requestClient.get<RuntimePage<RuntimeFact>>('/adx/runtime/clicks', {
    params,
  });
}

export function getRuntimeConversions(params: RuntimeQuery) {
  return requestClient.get<RuntimePage<RuntimeFact>>(
    '/adx/runtime/conversions',
    { params },
  );
}

export function getTaskIntegrationFacts(taskId: string, params: RuntimeQuery) {
  return requestClient.get<RuntimePage<RuntimeFact>>(
    `/adx/tasks/${taskId}/integration-facts`,
    { params },
  );
}

export function getRuntimeFailures(params: RuntimeQuery) {
  return requestClient.get<RuntimePage<RuntimeFailure>>('/adx/runtime/dlq', {
    params,
  });
}

export function getRuntimeExecution(id: string) {
  return requestClient.get<RuntimeTimeline>(`/adx/runtime/executions/${id}`);
}

export function previewRuntimeReplay(data: {
  expectedFailureRevision: number;
  failureId: string;
}) {
  return requestClient.post<RuntimeReplayPreview>(
    '/adx/runtime/replays:preview',
    data,
  );
}

export function confirmRuntimeReplay(data: {
  confirmed: true;
  expectedFailureRevision: number;
  failureId: string;
  previewToken: string;
  reason: string;
  requestId: string;
  ticket?: string;
}) {
  return requestClient.post<RuntimeReplayStatus>('/adx/runtime/replays', data);
}

export function getRuntimeReplay(id: string) {
  return requestClient.get<RuntimeReplayStatus>(`/adx/runtime/replays/${id}`);
}

export function previewRuntimeEmergencyStop(
  taskId: string,
  expectedTaskVersion: number,
) {
  return requestClient.post(
    `/adx/runtime/routes/${taskId}/emergency-stop:preview`,
    {
      expectedTaskVersion,
    },
  );
}

export function confirmRuntimeEmergencyStop(taskId: string, data: unknown) {
  return requestClient.post(
    `/adx/runtime/routes/${taskId}/emergency-stop`,
    data,
  );
}
