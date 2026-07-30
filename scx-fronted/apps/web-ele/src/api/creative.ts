import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type CreativeAsset = BaseRecord & {
  assetName: string;
  assetType: string;
  currentRevisionId?: string;
  fileHash?: string;
  fileSize?: number;
  fileUrl: string;
  mediaId: string;
  mimeType?: string;
  orgId: string;
  width?: number;
  height?: number;
};

export type CreativeAssetRevision = {
  createdAt?: string;
  fileHash?: string;
  fileSize?: number;
  fileUrl: string;
  height?: number;
  id: string;
  mimeType?: string;
  version: number;
  width?: number;
};

export type ReviewLog = {
  comment?: string;
  createdAt?: string;
  fromStatus?: string;
  id: string;
  operatorName?: string;
  toStatus: string;
};

export type ReviewCase = BaseRecord & {
  assetId: string;
  reviewComment?: string;
  submittedAt?: string;
  virusScanStatus: string;
};

export type LandingPage = BaseRecord & {
  content?: string;
  currentRevisionId?: string;
  orgId: string;
  pageName: string;
  pageType: string;
  schemaVersion: number;
  targetUrl: string;
};

export function getAssets(
  params: PageQuery & { mediaId: string; orgId: string },
) {
  return requestClient.get<PageResult<CreativeAsset>>('/ad/creative/assets', {
    params,
  });
}

export function createAsset(params: {
  assetName: string;
  assetType: string;
  fileHash: string;
  fileSize: number;
  fileUrl: string;
  mediaId: string;
  mimeType: string;
  orgId: string;
}) {
  return requestClient.post('/ad/creative/assets', undefined, { params });
}

export function uploadAsset(
  params: {
    assetName: string;
    assetType: 'IMAGE';
    height?: number;
    mediaId: string;
    orgId: string;
    width?: number;
  },
  file: File,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
) {
  const formData = new FormData();
  formData.append('mediaId', params.mediaId);
  formData.append('orgId', params.orgId);
  formData.append('assetType', params.assetType);
  formData.append('assetName', params.assetName);
  if (params.width) formData.append('width', String(params.width));
  if (params.height) formData.append('height', String(params.height));
  formData.append('file', file);
  return requestClient.post<CreativeAsset>(
    '/ad/creative/assets/upload',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event: { loaded: number; total?: number }) => {
        if (event.total && onProgress) {
          onProgress(Math.round((event.loaded * 100) / event.total));
        }
      },
      signal,
    },
  );
}

export function getAsset(id: string) {
  return requestClient.get<CreativeAsset>(`/ad/creative/assets/${id}`);
}

export function getAssetRevisions(id: string) {
  return requestClient.get<CreativeAssetRevision[]>(
    `/ad/creative/assets/${id}/revisions`,
  );
}

export function submitAsset(id: string) {
  return requestClient.post(`/ad/creative/assets/${id}/submit-review`);
}

export function publishAsset(id: string) {
  return requestClient.post(`/ad/creative/assets/${id}/publish`);
}

export function getReviewCases(
  params: PageQuery & { mediaId: string; orgId: string },
) {
  return requestClient.get<PageResult<ReviewCase>>(
    '/ad/creative/review-cases',
    { params },
  );
}

export function reviewAsset(
  id: string,
  decision: 'APPROVE' | 'REJECT',
  comment?: string,
) {
  return requestClient.post(
    `/ad/creative/review-cases/${id}/review`,
    undefined,
    { params: { comment, decision } },
  );
}

export function getReviewLogs(id: string) {
  return requestClient.get<ReviewLog[]>(`/ad/creative/review-cases/${id}/logs`);
}

export function getLandingPages(params: PageQuery & { orgId: string }) {
  return requestClient.get<PageResult<LandingPage>>(
    '/ad/creative/landing-pages',
    { params },
  );
}

export function saveLandingPage(data: Partial<LandingPage>) {
  const { id, ...params } = data;
  return id
    ? requestClient.put(`/ad/creative/landing-pages/${id}`, undefined, {
        params: { ...params, version: data.schemaVersion },
      })
    : requestClient.post('/ad/creative/landing-pages', undefined, { params });
}

export function publishLandingPage(id: string) {
  return requestClient.post(`/ad/creative/landing-pages/${id}/publish`);
}
