import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type CreativeAsset = BaseRecord & {
  assetName: string;
  assetType: string;
  currentRevisionId?: string;
  fileUrl: string;
  mediaId: string;
  orgId: string;
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

export function getLandingPages(
  params: PageQuery & { orgId: string },
) {
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
