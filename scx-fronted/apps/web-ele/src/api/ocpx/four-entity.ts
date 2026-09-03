import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type PlatformCode = 1 | 2 | 3 | 4;

export type OcpxUpstreamProtocol =
  | 'AERIM'
  | 'GRAVITY'
  | 'PHOENIX'
  | 'RENREN_VIDEO'
  | 'TEMPLATE_GET'
  | 'WEIFENG';

export type AdvertiserV2 = {
  advertiserName: string;
  balance?: number;
  bidUrl?: string;
  createTime: string;
  credentialConfigKey?: string;
  id: string;
  macroParams?: string;
  updateTime: string;
  upstreamProtocol?: OcpxUpstreamProtocol;
  username?: null | string;
};

export type AdvertiserQuery = {
  advertiserId?: string;
  current?: number;
  size?: number;
};

export type AdvertiserCreatePayload = {
  bidUrl?: string;
  credentialConfigKey?: string;
  macroParams?: string;
  name: string;
  password: string;
  upstreamProtocol: OcpxUpstreamProtocol;
  username: string;
};

export type AdvertiserUpdatePayload = {
  bidUrl?: string;
  credentialConfigKey?: string;
  macroParams?: string;
  name: string;
  upstreamProtocol: OcpxUpstreamProtocol;
};

export type ProtocolConfig = {
  allowedHosts: string[];
  protocol: Extract<
    OcpxUpstreamProtocol,
    'AERIM' | 'GRAVITY' | 'PHOENIX' | 'RENREN_VIDEO' | 'WEIFENG'
  >;
};

export type AdvertiserRechargeResult = {
  advertiserId: string;
  amount: number;
  balanceAfter: number;
  entryId: string;
  occurredAt: string;
};

export type AdvertiserDeductionResult = {
  advertiserId: string;
  amount: number;
  balanceAfter: number;
  entryId: string;
  occurredAt: string;
};

export type AdvertiserRoleMenuNode = {
  children: AdvertiserRoleMenuNode[];
  id: string;
  name: string;
  parentId?: string;
  permission?: string;
  requiresAllDataScope: boolean;
  type: string;
};

export type AdvertiserRolePermissions = {
  affectedAccountCount: number;
  allDataScope: boolean;
  menuTree: AdvertiserRoleMenuNode[];
  roleCode: string;
  roleId: string;
  roleName: string;
  selectedMenuIds: string[];
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
  billingEnabled: boolean;
  billingType: 1 | 2;
  bizId?: string;
  clkUrl: string;
  conversionEvent: ConversionEvent;
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

export type ConversionEvent =
  | 'ACTIVATE'
  | 'AUTHENTICATION'
  | 'CUSTOMER_ACQUISITION'
  | 'DOWNLOAD'
  | 'EVOKE'
  | 'FINISH_THE_ORDER'
  | 'FIRST_ACTIVATION'
  | 'FIRST_ORDER'
  | 'ACTIVITY'
  | 'ADDICTION'
  | 'LOGIN'
  | 'NEXT_STAY'
  | 'ORDER'
  | 'PAYMENT'
  | 'REGISTER'
  | 'VISITS';

export type MonitorTaskQuery = {
  current?: number;
  mediaId?: string;
  monitorTaskId?: string;
  size?: number;
};

export type MonitorTaskCreatePayload = {
  advertiserId: string;
  billingEnabled: boolean;
  billingType: 1 | 2;
  bizId?: string;
  conversionEvent: ConversionEvent;
  mediaId: string;
  monitorName: string;
  promotionLinkId: string;
  remark?: string;
  unitPrice: number;
};

export type MonitorTaskUpdatePayload = MonitorTaskCreatePayload;

export type EntityOption = { id: string; name: string };

export type PromotionAdvertiserOption = EntityOption & {
  protocol?: OcpxUpstreamProtocol;
};

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
    upstreamProtocol: data.upstreamProtocol,
    username: data.username,
  });
}
export function updateAdvertiserV2(id: string, data: AdvertiserUpdatePayload) {
  return requestClient.put<AdvertiserV2>(`/ocpx/advertisers/${id}`, {
    bidUrl: data.bidUrl,
    credentialConfigKey: data.credentialConfigKey,
    macroParams: data.macroParams,
    name: data.name,
    upstreamProtocol: data.upstreamProtocol,
  });
}
export function deleteAdvertiserV2(id: string) {
  return requestClient.delete(`/ocpx/advertisers/${id}`);
}
export function rechargeAdvertiser(
  id: string,
  data: { amount: number; remark?: string; requestId: string },
) {
  return requestClient.post<AdvertiserRechargeResult>(
    `/ocpx/advertisers/${id}/recharge`,
    data,
  );
}
export function deductAdvertiser(
  id: string,
  data: { amount: number; remark: string; requestId: string },
) {
  return requestClient.post<AdvertiserDeductionResult>(
    `/ocpx/advertisers/${id}/deduct`,
    data,
  );
}
export function getAdvertiserRolePermissions() {
  return requestClient.get<AdvertiserRolePermissions>(
    '/ocpx/advertisers/account-role/permissions',
  );
}
export function updateAdvertiserRolePermissions(data: {
  allDataScope: boolean;
  menuIds: string[];
}) {
  return requestClient.put<AdvertiserRolePermissions>(
    '/ocpx/advertisers/account-role/permissions',
    data,
  );
}

export function getProtocolConfigs() {
  return requestClient.get<ProtocolConfig[]>('/ocpx/protocol-configs');
}

export function updateProtocolAllowedHosts(
  protocol: ProtocolConfig['protocol'],
  allowedHosts: string[],
) {
  return requestClient.put<ProtocolConfig>(
    `/ocpx/protocol-configs/${protocol}/allowed-hosts`,
    { allowedHosts },
  );
}

export function listPromotionLinks(params: PromotionLinkQuery) {
  return requestClient.get<PageResult<PromotionLink>>('/ocpx/promotion-links', {
    params,
  });
}
export function promotionLinkAdvertisers(keyword?: string) {
  return requestClient.get<PromotionAdvertiserOption[]>(
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
export function deleteMedia(id: string) {
  return requestClient.delete(`/ocpx/media/${id}`);
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
    billingEnabled: data.billingEnabled,
    billingType: data.billingType,
    bizId: data.bizId,
    conversionEvent: data.conversionEvent,
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
