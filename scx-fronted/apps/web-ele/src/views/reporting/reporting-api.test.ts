import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createReportBackfill,
  getAttributionResults,
  getCallbackResults,
  syncMediaReport,
} from '#/api/ad';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('reporting pipeline API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('queries attribution and callback facts through their paged endpoints', async () => {
    vi.mocked(requestClient.get).mockResolvedValue({ records: [], total: 0 });
    const params = {
      current: 2,
      endDate: '2026-07-30',
      mediaId: 'media-1',
      size: 20,
      startDate: '2026-07-24',
    };

    await getAttributionResults(params);
    await getCallbackResults(params);

    expect(requestClient.get).toHaveBeenNthCalledWith(
      1,
      '/ad/reports/attribution-results',
      { params },
    );
    expect(requestClient.get).toHaveBeenNthCalledWith(
      2,
      '/ad/reports/callback-results',
      { params },
    );
  });

  it('keeps media sync and backfill as explicit management operations', async () => {
    vi.mocked(requestClient.post).mockResolvedValue({});
    const window = {
      end: '2026-07-30T20:00:00',
      start: '2026-07-30T19:00:00',
    };
    const backfill = {
      dryRun: true,
      endDate: '2026-07-30',
      mediaId: 'media-1',
      startDate: '2026-07-01',
    };

    await syncMediaReport('account-1', window);
    await createReportBackfill(backfill);

    expect(requestClient.post).toHaveBeenNthCalledWith(
      1,
      '/ad/reports/media-accounts/account-1/sync',
      undefined,
      { params: window },
    );
    expect(requestClient.post).toHaveBeenNthCalledWith(
      2,
      '/ad/reports/backfills',
      backfill,
    );
  });
});
