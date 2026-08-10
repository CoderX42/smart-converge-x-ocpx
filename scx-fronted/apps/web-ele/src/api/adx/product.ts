import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type ProductStatus = 'ACTIVE' | 'ARCHIVED' | 'DISABLED';

export type ProductEventPolicyInput = {
  dailyLimit: number;
  eventDefinitionId: string;
};

export type ProductEventPolicy = ProductEventPolicyInput & {
  eventCode: string;
  eventName: string;
  version: number;
};

export type ProductReferenceSummary = {
  appLinkCount: number;
  routeCount: number;
};

export type Product = {
  advertiserId: string;
  advertiserName: string;
  appLinkCount: number;
  code: string;
  configVersion: number;
  createTime: string;
  description?: string;
  eventPolicyCount: number;
  eventPolicyRevision: number;
  id: string;
  logoAvailable: boolean;
  logoUrl?: string;
  name: string;
  packageName?: string;
  republishRequiredCount: number;
  status: ProductStatus;
  updateTime: string;
};

export type ProductDetail = Omit<
  Product,
  'appLinkCount' | 'eventPolicyCount' | 'republishRequiredCount'
> & {
  archivedAt?: string;
  eventPolicies: ProductEventPolicy[];
  references: ProductReferenceSummary;
};

export type ProductAdvertiserOption = {
  code: string;
  id: string;
  name: string;
};

export type ProductEventOption = {
  eventCode: string;
  eventName: string;
  id: string;
};

export type ProductCreateInput = {
  advertiserId: string;
  code: string;
  description?: string;
  eventPolicies: ProductEventPolicyInput[];
  name: string;
  packageName?: string;
  status: Exclude<ProductStatus, 'ARCHIVED'>;
};

export type ProductUpdateInput = Omit<
  ProductCreateInput,
  'advertiserId' | 'code'
> & {
  expectedVersion: number;
};

export function getProducts(params: {
  advertiserId?: string;
  archived?: boolean;
  current: number;
  keyword?: string;
  size: number;
  status?: string;
}) {
  return requestClient.get<PageResult<Product>>('/adx/products', { params });
}

export function getProduct(id: string) {
  return requestClient.get<ProductDetail>(`/adx/products/${id}`);
}

export function getProductAdvertiserOptions(keyword?: string) {
  return requestClient.get<ProductAdvertiserOption[]>(
    '/adx/products/advertiser-options',
    { params: { keyword } },
  );
}

export function getProductEventOptions(keyword?: string) {
  return requestClient.get<ProductEventOption[]>(
    '/adx/products/event-options',
    { params: { keyword } },
  );
}

export function createProduct(
  data: ProductCreateInput,
  idempotencyKey: string,
) {
  return requestClient.post<ProductDetail>('/adx/products', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function updateProduct(id: string, data: ProductUpdateInput) {
  return requestClient.put<ProductDetail>(`/adx/products/${id}`, data);
}

export function archiveProduct(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/adx/products/${id}/archive`, {
    expectedVersion,
    reason,
  });
}

export function restoreProduct(
  id: string,
  expectedVersion: number,
  reason: string,
) {
  return requestClient.post(`/adx/products/${id}/restore`, {
    expectedVersion,
    reason,
  });
}

export function uploadProductLogo(
  id: string,
  expectedVersion: number,
  file: File,
) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<ProductDetail>(`/adx/products/${id}/logo`, data, {
    params: { expectedVersion },
  });
}

export function removeProductLogo(id: string, expectedVersion: number) {
  return requestClient.delete<ProductDetail>(`/adx/products/${id}/logo`, {
    params: { expectedVersion },
  });
}
