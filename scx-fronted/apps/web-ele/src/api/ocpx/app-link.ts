import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type AppLinkStatus = 'ACTIVE' | 'ARCHIVED' | 'DISABLED';
export type AppLinkMappingStatus = 'LEGACY_PENDING_PRODUCT' | 'READY';
export type AppLinkType = 'ANDROID' | 'IOS' | 'OTHER';

export type AppLinkReferenceSummary = {
  deductionRuleCount: number;
  monitorCount: number;
  publishedRouteCount: number;
  republishRequiredCount: number;
};

export type AppLinkRecord = {
  advertiserId: string;
  advertiserName: string;
  bizCode?: string;
  configVersion: number;
  createTime: string;
  deeplinkMasked?: string;
  documentId?: string;
  documentVersion: number;
  downloadMasked?: string;
  id: string;
  linkName: string;
  linkType?: AppLinkType;
  macroSet: string[];
  mappingStatus: AppLinkMappingStatus;
  productCode?: string;
  productId?: string;
  productName?: string;
  redirectEnabled: boolean;
  republishRequiredCount: number;
  status: AppLinkStatus;
  templateHost?: string;
  templateMasked?: string;
  updateTime: string;
};

export type AppLinkDetail = AppLinkRecord & {
  archivedAt?: string;
  deeplinkConfigured: boolean;
  description?: string;
  downloadConfigured: boolean;
  references: AppLinkReferenceSummary;
  templateConfigured: boolean;
  validationStatus: 'PASS' | 'PENDING_REVIEW';
};

export type AppLinkProductOption = {
  advertiserId: string;
  advertiserName: string;
  code: string;
  id: string;
  name: string;
};

export type AppLinkDocumentOption = {
  current: boolean;
  documentType: string;
  fileName: string;
  id: string;
  scanStatus: string;
  status: string;
  version: number;
};

export type AppLinkInput = {
  deeplinkUrl?: string;
  description?: string;
  documentId: string;
  downloadUrl?: string;
  linkName: string;
  linkType: AppLinkType;
  linkUrlTemplate: string;
  productId: string;
  redirectEnabled: boolean;
};

export type AppLinkCreateInput = AppLinkInput;

export type AppLinkUpdateInput = Omit<
  AppLinkInput,
  'deeplinkUrl' | 'downloadUrl'
> & {
  clearDeeplink: boolean;
  clearDownload: boolean;
  deeplinkUrl?: string;
  downloadUrl?: string;
  expectedVersion: number;
};

export type AppLinkValidationReport = {
  fingerprint?: string;
  host?: string;
  issues: string[];
  kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE';
  macroSet: string[];
  networkCalled: false;
  normalizedMaskedUrl?: string;
  status: 'BLOCK' | 'PASS';
};

export function getAppLinks(params: {
  advertiserId?: string;
  archived?: boolean;
  current: number;
  keyword?: string;
  linkType?: string;
  mappingStatus?: string;
  productId?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<AppLinkRecord>>('/ocpx/app-links', {
    params,
  });
}

export function getAppLink(id: string) {
  return requestClient.get<AppLinkDetail>(`/ocpx/app-links/${id}`);
}

export function getAppLinkProductOptions(params?: {
  advertiserId?: string;
  keyword?: string;
}) {
  return requestClient.get<AppLinkProductOption[]>(
    '/ocpx/app-links/product-options',
    { params },
  );
}

export function getAppLinkDocumentOptions(productId: string) {
  return requestClient.get<AppLinkDocumentOption[]>(
    '/ocpx/app-links/document-options',
    { params: { productId } },
  );
}

export function createAppLink(
  data: AppLinkCreateInput,
  idempotencyKey: string,
) {
  return requestClient.post<AppLinkDetail>('/ocpx/app-links', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateAppLink(id: string, data: AppLinkUpdateInput) {
  return requestClient.put<AppLinkDetail>(`/ocpx/app-links/${id}`, data);
}

export function completeAppLinkMapping(
  id: string,
  data: AppLinkInput & { expectedVersion: number },
) {
  return requestClient.post<AppLinkDetail>(
    `/ocpx/app-links/${id}/mapping:complete`,
    data,
  );
}

export function changeAppLinkStatus(
  id: string,
  expectedVersion: number,
  status: 'ACTIVE' | 'DISABLED',
  reason: string,
) {
  return requestClient.post(`/ocpx/app-links/${id}/status`, {
    expectedVersion,
    reason,
    status,
  });
}

export function archiveAppLink(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/ocpx/app-links/${id}/archive`, {
    expectedVersion,
    reason,
  });
}

export function restoreAppLink(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/ocpx/app-links/${id}/restore`, {
    expectedVersion,
    reason,
  });
}

export function validateAppLinkUrl(data: {
  kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE';
  linkType: AppLinkType;
  url: string;
}) {
  return requestClient.post<AppLinkValidationReport>(
    '/ocpx/app-links/url:validate',
    data,
  );
}
