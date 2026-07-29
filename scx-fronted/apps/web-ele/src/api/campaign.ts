import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type Campaign = BaseRecord & {
  adAccountId: string;
  billingMode: string;
  currency: 'CNY';
  dailyBudget?: number;
  endAt?: string;
  name: string;
  objective: string;
  startAt?: string;
  targetCpa?: number;
  timezone: 'Asia/Shanghai';
  totalBudget?: number;
};

export type AdGroup = BaseRecord & {
  bidCap?: number;
  campaignId: string;
  name: string;
};

export type Ad = BaseRecord & {
  adGroupId: string;
  creativeBindingId?: string;
  name: string;
};

export type MediaBinding = {
  campaignId: string;
  externalCampaignId?: string;
  externalStatus?: string;
  id: string;
  lastSyncedAt?: string;
  mediaAccountId: string;
  placementId: string;
};

export type CampaignDetail = {
  adGroups: AdGroup[];
  ads: Ad[];
  campaign: Campaign;
  mediaBindings: MediaBinding[];
  validationErrors: string[];
};

export type PublishingOperation = {
  campaignId: string;
  completedAt?: string;
  createdAt: string;
  id: string;
  operationType: string;
  resultDetail?: string;
  status: string;
};

export type CampaignRevision = {
  campaignId: string;
  createdAt: string;
  id: string;
  supersedes?: string;
  version: number;
};

export function getCampaigns(
  params: PageQuery & { adAccountId: string },
) {
  return requestClient.get<PageResult<Campaign>>('/ad/campaign/campaigns', {
    params,
  });
}

export function saveCampaign(data: Partial<Campaign>) {
  const { id, ...params } = data;
  return id
    ? requestClient.put(`/ad/campaign/campaigns/${id}`, undefined, {
        params: { ...params, version: data.version },
      })
    : requestClient.post('/ad/campaign/campaigns', undefined, { params });
}

export function getCampaignDetail(id: string) {
  return requestClient.get<CampaignDetail>(
    `/ad/campaign/campaigns/${id}/detail`,
  );
}

export function getCampaignOperations(id: string) {
  return requestClient.get<PageResult<PublishingOperation>>(
    `/ad/campaign/campaigns/${id}/operations`,
    { params: { current: 1, size: 100 } },
  );
}

export function getCampaignRevisions(id: string) {
  return requestClient.get<PageResult<CampaignRevision>>(
    `/ad/campaign/campaigns/${id}/revisions`,
    { params: { current: 1, size: 100 } },
  );
}

export function campaignAction(
  id: string,
  action:
    | 'approve'
    | 'archive'
    | 'end'
    | 'pause'
    | 'publish'
    | 'reject'
    | 'resume'
    | 'submit',
) {
  return requestClient.post(`/ad/campaign/campaigns/${id}/${action}`);
}

export function createAdGroup(params: {
  campaignId: string;
  dailyBudget?: number;
  eventCode: string;
  name: string;
  targetCpa?: number;
  targetingAst?: string;
  totalBudget?: number;
}) {
  return requestClient.post('/ad/campaign/ad-groups', undefined, { params });
}

export function createAd(params: {
  adGroupId: string;
  creativeBindingId?: string;
  name: string;
}) {
  return requestClient.post('/ad/campaign/ads', undefined, { params });
}
