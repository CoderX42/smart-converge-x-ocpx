import { beforeEach, describe, expect, it, vi } from 'vitest';

import { queryRta, saveRta } from './rta';

const client = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('#/api/request', () => ({ requestClient: client }));
describe('async RTA management contracts', () => {
  beforeEach(() => vi.clearAllMocks());
  it('keeps media identity and independent resource endpoints', async () => {
    await queryRta('records', { mediaId: 'media-a', requestId: 'request-a' });
    expect(client.get).toHaveBeenCalledWith('/ocpx/rta/records', {
      params: { mediaId: 'media-a', requestId: 'request-a' },
    });
    await saveRta('media', {
      mediaId: 'media-a',
      enabled: false,
      credentialRef: 'media-a-key',
      version: 2,
    });
    expect(client.post).toHaveBeenCalledWith('/ocpx/rta/media', {
      mediaId: 'media-a',
      enabled: false,
      credentialRef: 'media-a-key',
      version: 2,
    });
  });
});
