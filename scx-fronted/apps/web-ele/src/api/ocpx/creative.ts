import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type CreativeLink = { id: string; name: string };

export type Creative = {
  advertiserId: string;
  advertiserName: string;
  assetUrl: string;
  checksumSha256: string;
  createTime: string;
  creativeName: string;
  creativeType: 1;
  fileName: string;
  fileSize: number;
  height: number;
  id: string;
  mimeType: string;
  promotionLinks: CreativeLink[];
  remark?: string;
  sourceMaterialId: string;
  status: 0 | 1;
  updateTime: string;
  width: number;
};

export type CreativeAsset = {
  assetUrl: string;
  createTime: string;
  fileName: string;
  fileSize: number;
  sourceMaterialId: string;
};

export type UploadedCreativeAsset = CreativeAsset & {
  checksumSha256: string;
  duplicateCreativeIds: string[];
  height: number;
  mimeType: string;
  width: number;
};

export type CreativePayload = {
  advertiserId: string;
  creativeName: string;
  promotionLinkIds: string[];
  remark?: string;
  sourceMaterialId: string;
  status: 0 | 1;
};

export function listCreatives(params: Record<string, unknown>) {
  return requestClient.get<PageResult<Creative>>('/ocpx/creatives', { params });
}

export function listCreativeAssets(advertiserId: string, keyword?: string) {
  return requestClient.get<CreativeAsset[]>('/ocpx/creatives/assets', {
    params: { advertiserId, keyword },
  });
}

export function uploadCreativeAsset(advertiserId: string, file: File) {
  const data = new FormData();
  data.append('advertiserId', advertiserId);
  data.append('file', file);
  return requestClient.post<UploadedCreativeAsset>(
    '/ocpx/creatives/assets',
    data,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

export function createCreative(data: CreativePayload) {
  return requestClient.post<Creative>('/ocpx/creatives', data);
}

export function updateCreative(id: string, data: CreativePayload) {
  return requestClient.put<Creative>(`/ocpx/creatives/${id}`, data);
}

export function deleteCreative(id: string) {
  return requestClient.delete(`/ocpx/creatives/${id}`);
}

export function getCreativeBindings(id: string) {
  return requestClient.get<CreativeLink[]>(
    `/ocpx/creatives/${id}/promotion-links`,
  );
}

export function replaceCreativeBindings(
  id: string,
  promotionLinkIds: string[],
) {
  return requestClient.put<CreativeLink[]>(
    `/ocpx/creatives/${id}/promotion-links`,
    { promotionLinkIds },
  );
}
