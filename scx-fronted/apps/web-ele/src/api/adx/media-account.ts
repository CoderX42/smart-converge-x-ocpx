import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type MediaAccountStatus = 'ACTIVE' | 'ARCHIVED' | 'DISABLED' | 'PAUSED';

export type MediaAccountReferenceSummary = {
  publishedRouteCount: number;
  routeCount: number;
  taskCount: number;
};

export type MediaAccount = {
  activeCredentialCount: number;
  adapterCode?: string;
  channelId: string;
  channelName: string;
  code: string;
  configVersion: number;
  createTime: string;
  credentialCount: number;
  credentialRevision: number;
  description?: string;
  documentConfigured: boolean;
  id: string;
  name: string;
  ownerCount: number;
  publishedRouteCount: number;
  republishRequiredCount: number;
  status: MediaAccountStatus;
  updateTime: string;
};

export type MediaAccountDetail = Omit<
  MediaAccount,
  | 'activeCredentialCount'
  | 'credentialCount'
  | 'documentConfigured'
  | 'ownerCount'
  | 'publishedRouteCount'
  | 'republishRequiredCount'
> & {
  archivedAt?: string;
  defaultCredentialId?: string;
  documentId?: string;
  documentVersion: number;
  references: MediaAccountReferenceSummary;
};

export type MediaCredential = {
  createTime: string;
  description?: string;
  id: string;
  lastPreflightAt?: string;
  lastPreflightStatus?: 'BLOCKED' | 'READY';
  loginType: 'EMAIL';
  maskedIdentifier: string;
  secretConfigured: boolean;
  secretRevision: number;
  status: 'ACTIVE' | 'DISABLED' | 'PENDING' | 'REVOKED';
  updateTime: string;
  version: number;
};

export type MediaAccountOwner = {
  displayName: string;
  id: string;
  responsibility: string;
  status: string;
  userId: string;
  version: number;
};

export type MediaChannelOption = {
  adapterAvailable: boolean;
  adapterCode: string;
  authType: string;
  code: string;
  id: string;
  name: string;
  status: string;
};

export type MediaDocumentOption = {
  id: string;
  summary?: string;
  type: string;
  version: number;
};

export type MediaOwnerOption = { id: string; name: string; status: string };

export type MediaPreflight = {
  checkedAt: string;
  checks: Array<{
    code: string;
    level: 'BLOCK' | 'PASS' | 'WARN';
    message: string;
  }>;
  configVersion: number;
  credentialRevision: number;
  mode: 'CONFIG_ONLY';
  networkCalled: false;
  status: 'BLOCKED' | 'READY';
};

export type MediaAccountEditorInput = {
  channelId?: string;
  description?: string;
  documentId?: string;
  expectedVersion?: number;
  name: string;
};

export function getMediaAccounts(params: {
  archived?: boolean;
  channelId?: string;
  current: number;
  keyword?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<MediaAccount>>('/adx/media-accounts', {
    params,
  });
}

export function getMediaAccount(id: string) {
  return requestClient.get<MediaAccountDetail>(`/adx/media-accounts/${id}`);
}

export function getMediaChannelOptions(keyword?: string) {
  return requestClient.get<MediaChannelOption[]>(
    '/adx/media-accounts/channel-options',
    { params: { keyword } },
  );
}

export function getMediaDocumentOptions(channelId: string) {
  return requestClient.get<MediaDocumentOption[]>(
    '/adx/media-accounts/document-options',
    { params: { channelId } },
  );
}

export function getMediaOwnerOptions(accountId: string) {
  return requestClient.get<MediaOwnerOption[]>(
    '/adx/media-accounts/owner-options',
    { params: { accountId } },
  );
}

export function createMediaAccount(
  data: MediaAccountEditorInput,
  idempotencyKey: string,
) {
  return requestClient.post<MediaAccountDetail>('/adx/media-accounts', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateMediaAccount(id: string, data: MediaAccountEditorInput) {
  return requestClient.put<MediaAccountDetail>(
    `/adx/media-accounts/${id}`,
    data,
  );
}

export function changeMediaAccountStatus(
  id: string,
  data: { expectedVersion: number; reason?: string; status: string },
) {
  return requestClient.post(`/adx/media-accounts/${id}/status`, data);
}

export function archiveMediaAccount(
  id: string,
  expectedVersion: number,
  reason?: string,
) {
  return requestClient.post(`/adx/media-accounts/${id}/archive`, {
    expectedVersion,
    reason,
  });
}

export function restoreMediaAccount(
  id: string,
  expectedVersion: number,
  reason?: string,
) {
  return requestClient.post(`/adx/media-accounts/${id}/restore`, {
    expectedVersion,
    reason,
  });
}

export function getMediaCredentials(id: string) {
  return requestClient.get<MediaCredential[]>(
    `/adx/media-accounts/${id}/credentials`,
  );
}

export function createMediaCredential(
  id: string,
  data: {
    description?: string;
    loginIdentifier: string;
    loginType: 'EMAIL';
    secretInput: string;
  },
) {
  return requestClient.post<MediaCredential>(
    `/adx/media-accounts/${id}/credentials`,
    data,
  );
}

export function updateMediaCredential(
  id: string,
  credentialId: string,
  data: {
    description?: string;
    expectedVersion: number;
    loginIdentifier?: string;
  },
) {
  return requestClient.put<MediaCredential>(
    `/adx/media-accounts/${id}/credentials/${credentialId}`,
    data,
  );
}

export function rotateMediaCredential(
  id: string,
  credentialId: string,
  data: { expectedSecretRevision: number; reason: string; secretInput: string },
) {
  return requestClient.post<MediaCredential>(
    `/adx/media-accounts/${id}/credentials/${credentialId}:rotate`,
    data,
  );
}

export function changeMediaCredentialStatus(
  id: string,
  credentialId: string,
  data: { expectedVersion: number; reason?: string; status: string },
) {
  return requestClient.post<MediaCredential>(
    `/adx/media-accounts/${id}/credentials/${credentialId}/status`,
    data,
  );
}

export function getMediaAccountOwners(id: string) {
  return requestClient.get<MediaAccountOwner[]>(
    `/adx/media-accounts/${id}/owners`,
  );
}

export function replaceMediaAccountOwners(
  id: string,
  expectedVersion: number,
  userIds: string[],
) {
  return requestClient.put<MediaAccountOwner[]>(
    `/adx/media-accounts/${id}/owners`,
    { expectedVersion, userIds },
  );
}

export function preflightMediaAccount(id: string, expectedVersion: number) {
  return requestClient.post<MediaPreflight>(
    `/adx/media-accounts/${id}/connectivity:check`,
    { expectedVersion },
  );
}
