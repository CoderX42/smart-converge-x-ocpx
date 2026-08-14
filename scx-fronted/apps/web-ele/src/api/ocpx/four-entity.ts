import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type PlatformCode = 1 | 2 | 3 | 4;

export type AdvertiserV2 = {
  advertiserName: string;
  balance?: number;
  bidUrl: string;
  createTime: string;
  credentialConfigKey?: string;
  id: string;
  macroParams?: string;
  updateTime: string;
};

export type PromotionLink = {
  advertiserId: string;
  advertiserName: string;
  createTime: string;
  deeplink?: string;
  id: string;
  linkChannel?: string;
  linkExt?: string;
  linkName: string;
  linkUrl: string;
  platform: PlatformCode;
  remark?: string;
  updateTime: string;
};

export type Media = {
  createTime: string;
  credentialConfigKey?: string;
  id: string;
  macroParams?: string;
  mediaName: string;
  updateTime: string;
  uri: string;
};

export type MonitorTask = {
  advertiserId: string;
  advertiserName: string;
  bizId?: string;
  clkUrl: string;
  createTime: string;
  id: string;
  mediaId: string;
  mediaName: string;
  monitorName: string;
  platform: PlatformCode;
  promotionLinkId: string;
  promotionLinkName: string;
  remark?: string;
  showUrl: string;
  unitPrice?: number;
  updateTime: string;
};

export type EntityOption = { id: string; name: string };

export function listAdvertisers(params: Record<string, unknown>) {
  return requestClient.get<PageResult<AdvertiserV2>>('/ocpx/advertisers', {
    params,
  });
}
export function advertiserOptions() {
  return requestClient.get<EntityOption[]>('/ocpx/advertisers/options');
}
export function createAdvertiserV2(data: Record<string, unknown>) {
  return requestClient.post<AdvertiserV2>('/ocpx/advertisers', data);
}
export function updateAdvertiserV2(id: string, data: Record<string, unknown>) {
  return requestClient.put<AdvertiserV2>(`/ocpx/advertisers/${id}`, data);
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
  return requestClient.get<EntityOption[]>(
    '/ocpx/promotion-links/advertiser-options',
    { params: { keyword } },
  );
}
export function promotionLinkOptions(advertiserId?: string) {
  return requestClient.get<EntityOption[]>('/ocpx/promotion-links/options', {
    params: { advertiserId },
  });
}
export function createPromotionLink(data: Record<string, unknown>) {
  return requestClient.post<PromotionLink>('/ocpx/promotion-links', data);
}
export function updatePromotionLink(id: string, data: Record<string, unknown>) {
  return requestClient.put<PromotionLink>(`/ocpx/promotion-links/${id}`, data);
}
export function deletePromotionLink(id: string) {
  return requestClient.delete(`/ocpx/promotion-links/${id}`);
}

export function listMedia(params: Record<string, unknown>) {
  return requestClient.get<PageResult<Media>>('/ocpx/media', { params });
}
export function mediaOptions() {
  return requestClient.get<EntityOption[]>('/ocpx/media/options');
}
export function createMedia(data: Record<string, unknown>) {
  return requestClient.post<Media>('/ocpx/media', data);
}
export function updateMedia(id: string, data: Record<string, unknown>) {
  return requestClient.put<Media>(`/ocpx/media/${id}`, data);
}

export function listMonitorTasks(params: Record<string, unknown>) {
  return requestClient.get<PageResult<MonitorTask>>('/ocpx/monitor-tasks', {
    params,
  });
}
export function monitorTaskOptions(mediaId?: string) {
  return requestClient.get<EntityOption[]>('/ocpx/monitor-tasks/options', {
    params: { mediaId },
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
export function deleteMonitorTask(id: string) {
  return requestClient.delete(`/ocpx/monitor-tasks/${id}`);
}
