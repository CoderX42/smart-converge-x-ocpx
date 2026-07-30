import { beforeEach, describe, expect, it, vi } from 'vitest';

import { deleteProduct } from '#/api/ad';
import {
  deleteAgencyRelation,
  saveAdvertiser,
  saveDataAuthorization,
} from '#/api/commercial';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('commercial CRUD API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses the dedicated advertiser update contract', async () => {
    vi.mocked(requestClient.put).mockResolvedValue(true);

    await saveAdvertiser({
      advertiserName: '广告主一',
      id: 'advertiser-1',
      industry: '电商',
      orgId: 'org-1',
      websiteUrl: 'https://example.com',
    });

    expect(requestClient.put).toHaveBeenCalledWith(
      '/ad/commercial/advertisers/advertiser-1',
      {
        advertiserName: '广告主一',
        id: 'advertiser-1',
        industry: '电商',
        orgId: 'org-1',
        websiteUrl: 'https://example.com',
      },
    );
  });

  it('uses revoke semantics for agency relations and product reference guards', async () => {
    vi.mocked(requestClient.delete).mockResolvedValue(true);

    await deleteAgencyRelation('relation-1');
    await deleteProduct('product-1', 3);

    expect(requestClient.delete).toHaveBeenNthCalledWith(
      1,
      '/ad/commercial/agency-relations/relation-1',
    );
    expect(requestClient.delete).toHaveBeenNthCalledWith(
      2,
      '/ad/products/product-1',
      { params: { version: 3 } },
    );
  });

  it('maps UI organization fields to the data authorization DTO', async () => {
    vi.mocked(requestClient.post).mockResolvedValue(true);

    await saveDataAuthorization({
      authorizationType: 'REPORT_READ',
      granteeOrgId: 'org-2',
      grantorOrgId: 'org-1',
      scope: '["SUMMARY"]',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ad/commercial/data-authorizations',
      {
        authorizationType: 'REPORT_READ',
        endDate: undefined,
        granteeOrganizationId: 'org-2',
        grantorOrganizationId: 'org-1',
        scope: '["SUMMARY"]',
        startDate: undefined,
      },
    );
  });
});
