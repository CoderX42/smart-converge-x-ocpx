import { beforeEach, describe, expect, it, vi } from 'vitest';

import { listBillingEntries } from './billing';
import {
  createCreative,
  listCreativeAssets,
  replaceCreativeBindings,
  uploadCreativeAsset,
} from './creative';

const requestClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('ocpx V111 API contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('creates a creative from a managed material and a final link set', async () => {
    const payload = {
      advertiserId: 'advertiser-1',
      creativeName: '创意甲',
      promotionLinkIds: ['link-1', 'link-2'],
      sourceMaterialId: 'material-1',
      status: 1 as const,
    };

    await createCreative(payload);
    await replaceCreativeBindings('creative-1', []);

    expect(requestClient.post).toHaveBeenCalledWith('/ocpx/creatives', payload);
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/creatives/creative-1/promotion-links',
      { promotionLinkIds: [] },
    );
  });

  it('keeps advertiser ownership in material and ledger queries', async () => {
    await listCreativeAssets('advertiser-1', '图片');
    await listBillingEntries({
      advertiserId: 'advertiser-1',
      entryType: 'CONVERSION_CHARGE',
      status: 'REJECTED',
    });

    expect(requestClient.get).toHaveBeenNthCalledWith(
      1,
      '/ocpx/creatives/assets',
      { params: { advertiserId: 'advertiser-1', keyword: '图片' } },
    );
    expect(requestClient.get).toHaveBeenNthCalledWith(
      2,
      '/ocpx/billing-entries',
      {
        params: {
          advertiserId: 'advertiser-1',
          entryType: 'CONVERSION_CHARGE',
          status: 'REJECTED',
        },
      },
    );
  });

  it('uploads an asset as multipart data scoped to an advertiser', async () => {
    const file = new File(['image'], 'image.png', { type: 'image/png' });

    await uploadCreativeAsset('advertiser-1', file);

    const call = requestClient.post.mock.calls[0];
    expect(call).toBeDefined();
    if (!call) {
      throw new Error('upload request was not sent');
    }
    const [, body, options] = call;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('advertiserId')).toBe('advertiser-1');
    expect(body.get('file')).toBe(file);
    expect(options).toEqual({
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  });
});
