import { beforeEach, describe, expect, it, vi } from 'vitest';

import { deleteRta, queryRta, rtaOverview, rtaReadiness, saveRta } from './rta';

const client = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
}));
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
  it('reads route readiness and deletes with optimistic versions', async () => {
    await rtaOverview({ monitorTaskIds: 't1,t2', onlyIssues: 'true' });
    expect(client.get).toHaveBeenCalledWith('/ocpx/rta/overview', {
      params: { monitorTaskIds: 't1,t2', onlyIssues: 'true' },
    });
    await rtaReadiness('t1');
    expect(client.get).toHaveBeenCalledWith('/ocpx/rta/readiness', {
      params: { monitorTaskId: 't1' },
    });
    await deleteRta('bindings', 't1', 3);
    expect(client.delete).toHaveBeenCalledWith('/ocpx/rta/bindings/t1', {
      params: { version: 3 },
    });
  });
});
