import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type TaskStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT' | 'PAUSED' | 'READY';

export type RoutePublishCheck = {
  checkToken: string;
  configuration: {
    adapterRevision: number;
    advertiserRevision: number;
    appLinkRevision: number;
    dedupWindowSeconds: number;
    eventBindingCount: number;
    eventSnapshotSchemaVersion: number;
    exposureAsClick: boolean;
    exposureEnabled: boolean;
    mediaAccountRevision: number;
    mediaCredentialSecretRevision: number;
    productEventPolicyRevision: number;
    productRevision: number;
    routeSchemaVersion: number;
    rtaMode: 'OFF';
  };
  expiresAt: string;
  items: Array<{
    code: string;
    message: string;
    status: 'FAIL' | 'PASS' | 'WARN';
  }>;
  overallStatus: 'FAIL' | 'PASS' | 'WARN';
  taskConfigVersion: number;
  taskId: string;
};

export type RoutePublishResult = {
  active: boolean;
  distributionStatus: string;
  monitorId: string;
  publicationStatus: 'PUBLISHED';
  publishedAt: string;
  publishRequestId: string;
  routeVersion: number;
  routeVersionId: string;
  taskId: string;
};

export type RouteVersion = {
  acceptanceStatus: string;
  distributionStatus: string;
  eventSnapshotSchemaVersion: number;
  mediaCredentialSecretRevision: number;
  monitorId: string;
  publicationStatus: string;
  publishedAt: string;
  routeKind: 'CLICK' | 'EXPOSURE';
  routeVersion: number;
  routeVersionId: string;
  taskConfigRevision: number;
};

export type RouteDistribution = {
  active: boolean;
  appliedCount: number;
  failedCount: number;
  instances: Array<{
    ackedAt: string;
    appliedVersion: number;
    errorCode?: string;
    instanceId: string;
    status: 'APPLIED' | 'FAILED';
  }>;
  minimumRequiredAcks: number;
  overallStatus: string;
  taskId: string;
};

export type RoutePausePreview = {
  activeRouteCount: number;
  expiresAt: string;
  impact: string;
  previewToken: string;
  taskConfigVersion: number;
  taskId: string;
};

export type TaskReferenceSummary = {
  monitorCount: number;
  publishedMonitorCount: number;
  republishRequiredCount: number;
};

export type TaskRecord = {
  advertiserId: string;
  advertiserName: string;
  bizCode: string;
  channelId: string;
  channelName: string;
  clickAppLinkId: string;
  clickAppLinkName: string;
  configVersion: number;
  createTime: string;
  dedupWindowSeconds: number;
  exposureAppLinkId?: string;
  exposureAppLinkName?: string;
  exposureAsClick: boolean;
  exposureEnabled: boolean;
  id: string;
  mediaAccountId: string;
  mediaAccountName: string;
  mediaCredentialId: string;
  mediaCredentialSecretRevision: number;
  operatorName: string;
  operatorUserId: string;
  productId: string;
  productName: string;
  publishedTaskVersion: number;
  references: TaskReferenceSummary;
  republishRequired: boolean;
  rtaMode: 'OFF';
  status: TaskStatus;
  taskName: string;
  updateTime: string;
};

export type TaskDetail = TaskRecord & {
  archivedAt?: string;
  description?: string;
  republishReason: string;
};

export type TaskOptions = {
  advertisers: Array<{ id: string; name: string }>;
  appLinks: Array<{
    advertiserId: string;
    id: string;
    linkType: string;
    name: string;
    productId: string;
  }>;
  channels: Array<{ id: string; name: string }>;
  credentials: Array<{
    id: string;
    label: string;
    mediaAccountId: string;
    secretRevision: number;
    status: string;
  }>;
  mediaAccounts: Array<{
    channelId: string;
    defaultCredentialId?: string;
    id: string;
    name: string;
  }>;
  operators: Array<{ displayName: string; id: string }>;
  products: Array<{ advertiserId: string; id: string; name: string }>;
};

export type TaskInput = {
  clickAppLinkId: string;
  dedupWindowSeconds: number;
  description?: string;
  eventBindings?: TaskEventBindingInput[];
  exposureAppLinkId?: string;
  exposureAsClick: boolean;
  exposureEnabled: boolean;
  mediaAccountId: string;
  mediaCredentialId: string;
  operatorUserId: string;
  productId: string;
  rtaMode: 'OFF';
  taskName: string;
};

export type TaskEventBindingInput = {
  callbackRatioBasisPoints: number;
  channelEventMappingId: string;
  eventDefinitionId: string;
  limitValue?: number;
  overLimitDelivery: boolean;
};

export type TaskEventBinding = TaskEventBindingInput & {
  channelEventMappingRevision: number;
  channelId: string;
  configVersion: number;
  eventCode: string;
  eventDefinitionRevision: number;
  eventName: string;
  externalCode: string;
  id: string;
  limitTimezone: 'Asia/Shanghai';
  limitWindow: 'NATURAL_DAY';
  samplingAlgorithm: 'SHA256_MOD_10000_V1';
  sourceId?: string;
  sourceRevision: number;
  sourceType: 'MANUAL' | 'PRODUCT_DEFAULT';
  status: string;
  taskId: string;
};

export type TaskEventBindingOptions = {
  adapterCode: string;
  channelId: string;
  definitions: Array<{
    billingEligible: boolean;
    code: string;
    configVersion: number;
    direction: string;
    id: string;
    name: string;
    standardType: string;
  }>;
  limitTimezone: 'Asia/Shanghai';
  limitWindow: 'NATURAL_DAY';
  mappings: Array<{
    adapterCode: string;
    channelId: string;
    configVersion: number;
    direction: string;
    eventDefinitionId: string;
    externalCode: string;
    id: string;
    parameterTemplateCode: string;
  }>;
  productTemplates: Array<{
    applicable: boolean;
    channelEventMappingId?: string;
    eventCode: string;
    eventDefinitionId: string;
    eventName: string;
    limitValue?: number;
    message: string;
    productPolicyId: string;
    productPolicyRevision: number;
  }>;
  samplingAlgorithm: 'SHA256_MOD_10000_V1';
};

export type TaskEventBindingsResult = {
  affectedMonitorCount: number;
  bindings: TaskEventBinding[];
  republishRequired: boolean;
  taskConfigVersion: number;
  taskId: string;
};

export function getTasks(params: {
  advertiserId?: string;
  archived?: boolean;
  channelId?: string;
  current: number;
  exposureEnabled?: boolean;
  keyword?: string;
  mediaAccountId?: string;
  operatorUserId?: string;
  productId?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<TaskRecord>>('/ocpx/tasks', { params });
}

export function getTask(id: string) {
  return requestClient.get<TaskDetail>(`/ocpx/tasks/${id}`);
}

export function getTaskOptions(keyword?: string) {
  return requestClient.get<TaskOptions>('/ocpx/tasks/options', {
    params: { keyword },
  });
}

export function getTaskEventBindings(id: string) {
  return requestClient.get<TaskEventBindingsResult>(
    `/ocpx/tasks/${id}/event-bindings`,
  );
}

export function getTaskEventBindingOptions(params: {
  channelId: string;
  mediaAccountId: string;
  productId: string;
}) {
  return requestClient.get<TaskEventBindingOptions>(
    '/ocpx/tasks/event-binding-options',
    { params },
  );
}

export function createTask(data: TaskInput, idempotencyKey: string) {
  return requestClient.post<TaskDetail>('/ocpx/tasks', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateTask(
  id: string,
  data: TaskInput & { expectedVersion: number },
) {
  return requestClient.put<TaskDetail>(`/ocpx/tasks/${id}`, data);
}

export function changeTaskStatus(
  id: string,
  expectedVersion: number,
  status: 'DRAFT' | 'READY',
  reason: string,
) {
  return requestClient.post(`/ocpx/tasks/${id}/status`, {
    expectedVersion,
    reason,
    status,
  });
}

export function archiveTask(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/ocpx/tasks/${id}/archive`, {
    expectedVersion,
    reason,
  });
}

export function restoreTask(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/ocpx/tasks/${id}/restore`, {
    expectedVersion,
    reason,
  });
}

export function getTaskPublishCheck(id: string) {
  return requestClient.get<RoutePublishCheck>(
    `/ocpx/tasks/${id}/publish-check`,
  );
}

export function publishTaskRoute(
  id: string,
  data: {
    checkToken: string;
    expectedTaskVersion: number;
    publishRequestId: string;
  },
) {
  return requestClient.post<RoutePublishResult>(
    `/ocpx/tasks/${id}/publish`,
    data,
  );
}

export function getTaskRouteVersions(id: string) {
  return requestClient.get<RouteVersion[]>(`/ocpx/tasks/${id}/route-versions`);
}

export function getTaskDistribution(id: string) {
  return requestClient.get<RouteDistribution>(`/ocpx/tasks/${id}/distribution`);
}

export function copyTaskRouteAddress(id: string) {
  return requestClient.get<{ auditId: string; template: string }>(
    `/ocpx/tasks/${id}/route-address`,
  );
}

export function previewTaskPause(id: string, expectedTaskVersion: number) {
  return requestClient.post<RoutePausePreview>(
    `/ocpx/tasks/${id}/pause:preview`,
    {
      expectedTaskVersion,
    },
  );
}

export function pauseTaskRoute(
  id: string,
  data: {
    confirmed: true;
    expectedTaskVersion: number;
    pauseRequestId: string;
    previewToken: string;
    reason: string;
  },
) {
  return requestClient.post(`/ocpx/tasks/${id}/pause`, data);
}
