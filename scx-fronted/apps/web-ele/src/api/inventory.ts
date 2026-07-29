import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type MediaAccount = BaseRecord & {
  accountIdExternal?: string;
  accountName: string;
  adapterType: string;
  currency: 'CNY';
  mediaId: string;
  timezone: 'Asia/Shanghai';
};

export type Placement = BaseRecord & {
  mediaAccountId: string;
  placementCode: string;
  placementName: string;
  placementType: string;
  supportedCreatives?: string;
};

export type Capability = {
  billingModes?: string;
  creativeFormats?: string;
  mediaManagedOcpc?: number;
  placementId: string;
  reportPullSupported?: number;
  statusSyncSupported?: number;
  targetEvents?: string;
  targetingFields?: string;
};

export function getMediaAccounts(
  params: PageQuery & { mediaId: string },
) {
  return requestClient.get<PageResult<MediaAccount>>(
    '/ad/inventory/media-accounts',
    { params },
  );
}

export function saveMediaAccount(data: Partial<MediaAccount>) {
  const { id, ...params } = data;
  return id
    ? requestClient.put(`/ad/inventory/media-accounts/${id}`, undefined, {
        params,
      })
    : requestClient.post('/ad/inventory/media-accounts', undefined, {
        params,
      });
}

export function changeMediaAccountStatus(id: string, status: string) {
  return requestClient.put(
    `/ad/inventory/media-accounts/${id}/status`,
    undefined,
    { params: { status } },
  );
}

export function getPlacements(
  params: PageQuery & { mediaAccountId: string; type?: string },
) {
  return requestClient.get<PageResult<Placement>>(
    '/ad/inventory/placements',
    { params },
  );
}

export function savePlacement(data: Partial<Placement>) {
  const { id, ...params } = data;
  return id
    ? requestClient.put(`/ad/inventory/placements/${id}`, undefined, {
        params: {
          creatives: params.supportedCreatives,
          name: params.placementName,
          type: params.placementType,
        },
      })
    : requestClient.post('/ad/inventory/placements', undefined, {
        params: {
          code: params.placementCode,
          creatives: params.supportedCreatives,
          mediaAccountId: params.mediaAccountId,
          name: params.placementName,
          type: params.placementType,
        },
      });
}

export function getCapability(id: string) {
  return requestClient.get<Capability>(
    `/ad/inventory/placements/${id}/capabilities`,
  );
}

export function saveCapability(id: string, params: Partial<Capability>) {
  return requestClient.put(
    `/ad/inventory/placements/${id}/capabilities`,
    undefined,
    { params },
  );
}

export function getAdapterHealth(mediaAccountId: string) {
  return requestClient.get<boolean>(
    `/ad/inventory/adapter/${mediaAccountId}/health`,
  );
}
