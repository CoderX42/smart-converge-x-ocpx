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
  username?: null | string;
};

export type AdvertiserQuery = {
  advertiserId?: string;
  current?: number;
  size?: number;
};

export type AdvertiserCreatePayload = {
  bidUrl: string;
  credentialConfigKey?: string;
  macroParams?: string;
  name: string;
  password: string;
  username: string;
};

export type AdvertiserUpdatePayload = Omit<
  AdvertiserCreatePayload,
  'password' | 'username'
>;

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

export type PromotionLinkQuery = {
  advertiserId?: string;
  current?: number;
  promotionLinkId?: string;
  size?: number;
};

export type PromotionLinkCreatePayload = {
  advertiserId: string;
  deeplink?: string;
  linkChannel?: string;
  linkExt?: string;
  linkName: string;
  linkUrl: string;
  platform: PlatformCode;
  remark?: string;
};

export type PromotionLinkUpdatePayload = PromotionLinkCreatePayload;

export type Media = {
  createTime: string;
  credentialConfigKey?: string;
  id: string;
  macroParams?: string;
  mediaName: string;
  updateTime: string;
  uri: string;
};

export type MediaQuery = {
  current?: number;
  mediaId?: string;
  size?: number;
};

export type MediaCreatePayload = {
  credentialConfigKey?: string;
  macroParams: string;
  mediaName: string;
  uri: string;
};

export type MediaUpdatePayload = MediaCreatePayload;

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

export type MonitorTaskQuery = {
  current?: number;
  mediaId?: string;
  monitorTaskId?: string;
  size?: number;
};

export type MonitorTaskCreatePayload = {
  advertiserId: string;
  bizId?: string;
  mediaId: string;
  monitorName: string;
  promotionLinkId: string;
  remark?: string;
  unitPrice: number;
};

export type MonitorTaskUpdatePayload = MonitorTaskCreatePayload;

export type EntityOption = { id: string; name: string };

export function listAdvertisers(params: AdvertiserQuery) {
  return requestClient.get<PageResult<AdvertiserV2>>('/ocpx/advertisers', {
    params,
  });
}
export function advertiserOptions() {
  return requestClient.get<EntityOption[]>('/ocpx/advertisers/options');
}
export function createAdvertiserV2(data: AdvertiserCreatePayload) {
  return requestClient.post<AdvertiserV2>('/ocpx/advertisers', {
    bidUrl: data.bidUrl,
    credentialConfigKey: data.credentialConfigKey,
    macroParams: data.macroParams,
    name: data.name,
    password: data.password,
    username: data.username,
  });
}
export function updateAdvertiserV2(id: string, data: AdvertiserUpdatePayload) {
  return requestClient.put<AdvertiserV2>(`/ocpx/advertisers/${id}`, {
    bidUrl: data.bidUrl,
    credentialConfigKey: data.credentialConfigKey,
    macroParams: data.macroParams,
    name: data.name,
  });
}
export function rechargeAdvertiser(id: string, balance: number) {
  return requestClient.post(`/ocpx/advertisers/${id}/recharge`, { balance });
}

export function listPromotionLinks(params: PromotionLinkQuery) {
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
function promotionLinkBody(
  data: PromotionLinkCreatePayload | PromotionLinkUpdatePayload,
) {
  return {
    advertiserId: data.advertiserId,
    deeplink: data.deeplink,
    linkChannel: data.linkChannel,
    linkExt: data.linkExt,
    linkName: data.linkName,
    linkUrl: data.linkUrl,
    platform: data.platform,
    remark: data.remark,
  };
}
export function createPromotionLink(data: PromotionLinkCreatePayload) {
  return requestClient.post<PromotionLink>(
    '/ocpx/promotion-links',
    promotionLinkBody(data),
  );
}
export function updatePromotionLink(
  id: string,
  data: PromotionLinkUpdatePayload,
) {
  return requestClient.put<PromotionLink>(
    `/ocpx/promotion-links/${id}`,
    promotionLinkBody(data),
  );
}
export function deletePromotionLink(id: string) {
  return requestClient.delete(`/ocpx/promotion-links/${id}`);
}

export function listMedia(params: MediaQuery) {
  return requestClient.get<PageResult<Media>>('/ocpx/media', { params });
}
export function mediaOptions() {
  return requestClient.get<EntityOption[]>('/ocpx/media/options');
}
function mediaBody(data: MediaCreatePayload | MediaUpdatePayload) {
  return {
    credentialConfigKey: data.credentialConfigKey,
    macroParams: data.macroParams,
    mediaName: data.mediaName,
    uri: data.uri,
  };
}
export function createMedia(data: MediaCreatePayload) {
  return requestClient.post<Media>('/ocpx/media', mediaBody(data));
}
export function updateMedia(id: string, data: MediaUpdatePayload) {
  return requestClient.put<Media>(`/ocpx/media/${id}`, mediaBody(data));
}

export function listMonitorTasks(params: MonitorTaskQuery) {
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
function monitorTaskBody(
  data: MonitorTaskCreatePayload | MonitorTaskUpdatePayload,
) {
  return {
    advertiserId: data.advertiserId,
    bizId: data.bizId,
    mediaId: data.mediaId,
    monitorName: data.monitorName,
    promotionLinkId: data.promotionLinkId,
    remark: data.remark,
    unitPrice: data.unitPrice,
  };
}
export function createMonitorTask(data: MonitorTaskCreatePayload) {
  return requestClient.post<MonitorTask>(
    '/ocpx/monitor-tasks',
    monitorTaskBody(data),
  );
}
export function updateMonitorTask(id: string, data: MonitorTaskUpdatePayload) {
  return requestClient.put<MonitorTask>(
    `/ocpx/monitor-tasks/${id}`,
    monitorTaskBody(data),
  );
}
export function deleteMonitorTask(id: string) {
  return requestClient.delete(`/ocpx/monitor-tasks/${id}`);
}
