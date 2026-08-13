import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type AdvertiserV2 = {
  adapterCode: string;
  advertiserCode: string;
  advertiserName: string;
  balance?: number;
  bidUrl: string;
  configVersion: number;
  createTime: string;
  credentialConfigKey: string;
  credentialVersion: number;
  id: string;
  macroParams?: string;
  status: 'ACTIVE' | 'DISABLED';
  updateTime: string;
  upstreamHostAllowlist?: string;
};

export type PromotionLink = {
  advertiserId: string;
  advertiserName: string;
  configVersion: number;
  createTime: string;
  deeplinkUrl?: string;
  downloadUrl?: string;
  id: string;
  linkChannel?: string;
  linkCode: string;
  linkName: string;
  linkType: 'DEEPLINK' | 'DOWNLOAD' | 'LANDING' | 'OTHER';
  linkUrlTemplate: string;
  macroSet: string[];
  packageName?: string;
  platform: 'ANDROID' | 'IOS' | 'OTHER' | 'WEB';
  redirectEnabled: boolean;
  remark?: string;
  status: 'ACTIVE' | 'DISABLED';
  updateTime: string;
};

export type Media = {
  adapterCode: string;
  authType: 'NONE' | 'SIGNATURE' | 'TOKEN';
  callbackHostAllowlist: string[];
  callbackUrl?: string;
  configVersion: number;
  createTime: string;
  credentialConfigKey?: string;
  credentialVersion?: number;
  id: string;
  ingressUri: string;
  macroParams: string[];
  mediaCode: string;
  mediaName: string;
  status: 'ACTIVE' | 'DISABLED';
  updateTime: string;
};

export type MonitorTask = {
  advertiserId: string;
  advertiserName: string;
  archivedAt?: string;
  bizId?: string;
  clkUrl: string;
  configVersion: number;
  createTime: string;
  id: string;
  mediaId: string;
  mediaName: string;
  operatorUserId?: string;
  promotionLinkId: string;
  promotionLinkName: string;
  remark?: string;
  showUrl: string;
  status: 'ACTIVE' | 'ARCHIVED' | 'PAUSED';
  taskCode: string;
  taskName: string;
  unitPrice: number;
  updateTime: string;
};

export type VersionedAction = { expectedVersion: number };

export function listAdvertisers(params: Record<string, unknown>) {
  return requestClient.get<PageResult<AdvertiserV2>>('/ocpx/advertisers', {
    params,
  });
}
export function createAdvertiserV2(data: Record<string, unknown>) {
  return requestClient.post<AdvertiserV2>('/ocpx/advertisers', data);
}
export function updateAdvertiserV2(id: string, data: Record<string, unknown>) {
  return requestClient.put<AdvertiserV2>(`/ocpx/advertisers/${id}`, data);
}
export function changeAdvertiserStatusV2(
  id: string,
  data: VersionedAction & { status: 'ACTIVE' | 'DISABLED' },
) {
  return requestClient.post(`/ocpx/advertisers/${id}/status`, data);
}
export function archiveAdvertiser(id: string, data: VersionedAction) {
  return requestClient.post(`/ocpx/advertisers/${id}/archive`, data);
}
export function rechargeAdvertiser(id: string, balance: number) {
  return requestClient.post(`/ocpx/advertisers/${id}/recharge`, { balance });
}

export function listPromotionLinks(params: Record<string, unknown>) {
  return requestClient.get<PageResult<PromotionLink>>('/ocpx/promotion-links', {
    params,
  });
}
export function promotionLinkAdvertisers(keyword?: string) {
  return requestClient.get<Array<{ code: string; id: string; name: string }>>(
    '/ocpx/promotion-links/advertiser-options',
    { params: { keyword } },
  );
}
export function createPromotionLink(data: Record<string, unknown>) {
  return requestClient.post<PromotionLink>('/ocpx/promotion-links', data);
}
export function updatePromotionLink(id: string, data: Record<string, unknown>) {
  return requestClient.put<PromotionLink>(`/ocpx/promotion-links/${id}`, data);
}
export function changePromotionLinkStatus(
  id: string,
  data: VersionedAction & { status: 'ACTIVE' | 'DISABLED' },
) {
  return requestClient.post(`/ocpx/promotion-links/${id}/status`, data);
}
export function archivePromotionLink(id: string, data: VersionedAction) {
  return requestClient.post(`/ocpx/promotion-links/${id}/archive`, data);
}
export function validatePromotionLinkUrl(url: string) {
  return requestClient.post('/ocpx/promotion-links/url:validate', { url });
}

export function listMedia(params: Record<string, unknown>) {
  return requestClient.get<PageResult<Media>>('/ocpx/media', { params });
}
export function createMedia(data: Record<string, unknown>) {
  return requestClient.post<Media>('/ocpx/media', data);
}
export function updateMedia(id: string, data: Record<string, unknown>) {
  return requestClient.put<Media>(`/ocpx/media/${id}`, data);
}
export function changeMediaStatus(
  id: string,
  data: VersionedAction & { status: 'ACTIVE' | 'DISABLED' },
) {
  return requestClient.post(`/ocpx/media/${id}/status`, data);
}
export function preflightMedia(id: string) {
  return requestClient.post<{ issues: string[]; status: 'BLOCK' | 'PASS' }>(
    `/ocpx/media/${id}/preflight`,
  );
}
export function checkMediaConnectivity(id: string) {
  return requestClient.post<{
    message: string;
    networkCalled: boolean;
    status: string;
  }>(`/ocpx/media/${id}/connectivity-check`);
}
export function archiveMedia(id: string, data: VersionedAction) {
  return requestClient.post(`/ocpx/media/${id}/archive`, data);
}

export function listMonitorTasks(params: Record<string, unknown>) {
  return requestClient.get<PageResult<MonitorTask>>('/ocpx/monitor-tasks', {
    params,
  });
}
export function getMonitorTask(id: string) {
  return requestClient.get<MonitorTask>(`/ocpx/monitor-tasks/${id}`);
}
export function createMonitorTask(data: Record<string, unknown>) {
  return requestClient.post<MonitorTask>('/ocpx/monitor-tasks', data);
}
export function updateMonitorTask(id: string, data: Record<string, unknown>) {
  return requestClient.put<MonitorTask>(`/ocpx/monitor-tasks/${id}`, data);
}
export function changeMonitorTaskStatus(
  id: string,
  data: VersionedAction & { status: 'ACTIVE' | 'PAUSED' },
) {
  return requestClient.post(`/ocpx/monitor-tasks/${id}/status`, data);
}
export function archiveMonitorTask(id: string, data: VersionedAction) {
  return requestClient.post(`/ocpx/monitor-tasks/${id}/archive`, data);
}
export function deleteMonitorTask(id: string) {
  return requestClient.delete(`/ocpx/monitor-tasks/${id}`);
}
