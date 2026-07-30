import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getMediaAccounts, saveMediaAccount } from '#/api/inventory';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('media account channel API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('filters media accounts by their delivery channel', async () => {
    vi.mocked(requestClient.get).mockResolvedValue({ records: [], total: 0 });

    await getMediaAccounts({
      channelId: 'channel-1',
      current: 2,
      keyword: '主账户',
      mediaId: 'media-1',
      size: 20,
    });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ad/inventory/media-accounts',
      {
        params: {
          channelId: 'channel-1',
          current: 2,
          keyword: '主账户',
          mediaId: 'media-1',
          size: 20,
        },
      },
    );
  });

  it('sends the selected channel for create and reassignment', async () => {
    vi.mocked(requestClient.post).mockResolvedValue(true);
    vi.mocked(requestClient.put).mockResolvedValue(true);

    await saveMediaAccount({
      accountName: '主账户',
      adapterType: 'GENERIC_SANDBOX',
      channelId: 'channel-1',
      mediaId: 'media-1',
    });
    await saveMediaAccount({
      accountName: '主账户',
      adapterType: 'GENERIC_SANDBOX',
      channelId: 'channel-2',
      id: 'account-1',
      mediaId: 'media-1',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ad/inventory/media-accounts',
      undefined,
      {
        params: {
          accountName: '主账户',
          adapterType: 'GENERIC_SANDBOX',
          channelId: 'channel-1',
          mediaId: 'media-1',
        },
      },
    );
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ad/inventory/media-accounts/account-1',
      undefined,
      {
        params: {
          accountName: '主账户',
          adapterType: 'GENERIC_SANDBOX',
          channelId: 'channel-2',
          mediaId: 'media-1',
        },
      },
    );
  });
});
