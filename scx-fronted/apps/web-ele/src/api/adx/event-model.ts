import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type EventReferenceSummary = {
  codeLocked: boolean;
  productDefaultCount: number;
  publishedSnapshotCount: number;
  statusChangeRequiresConfirmation: boolean;
  taskBindingCount: number;
};

export type CanonicalEventDefinition = {
  billingEligible: boolean;
  code: string;
  configVersion: number;
  createTime: string;
  description?: string;
  direction: 'BIDIRECTIONAL' | 'DOWNSTREAM_DELIVERY' | 'UPSTREAM_CALLBACK';
  id: string;
  mappingState: 'CANONICAL';
  name: string;
  references: EventReferenceSummary;
  sortOrder: number;
  standardType: string;
  status: 'ACTIVE' | 'ARCHIVED' | 'DISABLED';
  subjectType: 'ACCOUNT' | 'DEVICE' | 'LEAD' | 'ORDER';
  updateTime: string;
};

export type CanonicalEventMapping = {
  adapterCapabilityRevision: number;
  adapterCode: string;
  channelId: string;
  channelName: string;
  configVersion: number;
  createTime: string;
  description?: string;
  direction: 'DOWNSTREAM_DELIVERY' | 'UPSTREAM_CALLBACK';
  eventDefinitionCode: string;
  eventDefinitionId: string;
  eventDefinitionName: string;
  externalCode: string;
  id: string;
  mappingState: 'CANONICAL';
  parameterTemplateCode: 'FLAT_EVENT_V1' | 'STANDARD_EVENT_V1';
  references: EventReferenceSummary;
  status: 'ACTIVE' | 'ARCHIVED' | 'DISABLED';
  successRuleCode: 'BODY_CODE_ZERO' | 'HTTP_2XX';
  updateTime: string;
};

export type EventDefinitionCreateInput = {
  billingEligible: boolean;
  code: string;
  description?: string;
  direction: CanonicalEventDefinition['direction'];
  name: string;
  sortOrder: number;
  standardType: string;
  status: 'ACTIVE' | 'DISABLED';
  subjectType: CanonicalEventDefinition['subjectType'];
};

export type EventDefinitionUpdateInput = Omit<
  EventDefinitionCreateInput,
  'code' | 'status'
> & { expectedVersion: number };

export type EventMappingCreateInput = {
  adapterCode: string;
  channelId: string;
  description?: string;
  direction: CanonicalEventMapping['direction'];
  eventDefinitionId: string;
  externalCode: string;
  parameterTemplateCode: CanonicalEventMapping['parameterTemplateCode'];
  status: 'ACTIVE' | 'DISABLED';
  successRuleCode: CanonicalEventMapping['successRuleCode'];
};

export type EventMappingUpdateInput = Omit<
  EventMappingCreateInput,
  'adapterCode' | 'channelId' | 'status'
> & { expectedVersion: number };

export function listCanonicalEventDefinitions(params: {
  current: number;
  direction?: string;
  keyword?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<CanonicalEventDefinition>>(
    '/adx/events',
    { params },
  );
}

export function createCanonicalEventDefinition(
  data: EventDefinitionCreateInput,
  idempotencyKey: string,
) {
  return requestClient.post<CanonicalEventDefinition>('/adx/events', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateCanonicalEventDefinition(
  id: string,
  data: EventDefinitionUpdateInput,
) {
  return requestClient.put<CanonicalEventDefinition>(`/adx/events/${id}`, data);
}

export function changeCanonicalEventDefinitionStatus(
  id: string,
  data: {
    confirmed: boolean;
    expectedVersion: number;
    reason?: string;
    status: CanonicalEventDefinition['status'];
  },
) {
  return requestClient.post(`/adx/events/${id}/status`, data);
}

export function listCanonicalEventMappings(params: {
  channelId?: string;
  current: number;
  direction?: string;
  keyword?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<CanonicalEventMapping>>(
    '/adx/event-mappings',
    { params },
  );
}

export function createCanonicalEventMapping(
  data: EventMappingCreateInput,
  idempotencyKey: string,
) {
  return requestClient.post<CanonicalEventMapping>(
    '/adx/event-mappings',
    data,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  );
}

export function updateCanonicalEventMapping(
  id: string,
  data: EventMappingUpdateInput,
) {
  return requestClient.put<CanonicalEventMapping>(
    `/adx/event-mappings/${id}`,
    data,
  );
}

export function changeCanonicalEventMappingStatus(
  id: string,
  data: {
    confirmed: boolean;
    expectedVersion: number;
    reason?: string;
    status: CanonicalEventMapping['status'];
  },
) {
  return requestClient.post(`/adx/event-mappings/${id}/status`, data);
}
