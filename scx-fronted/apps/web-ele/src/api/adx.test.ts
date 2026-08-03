import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  exportReport,
  getCatalog,
  getReport,
  getReportOptions,
  saveCatalog,
  updateClueStatus,
  updateClueStatusByFilter,
} from './adx';

const requestClient = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('aDX API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('queries the compact channel endpoint with pagination', () => {
    getCatalog('channel', { current: 2, size: 20 });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/channels', {
      params: { current: 2, size: 20 },
    });
  });

  it('uses POST for create and PUT for edit', () => {
    saveCatalog('advertiser', { advertiserName: 'A' });
    saveCatalog('advertiser', { advertiserName: 'A', id: 'adv-1' });
    expect(requestClient.post).toHaveBeenCalledWith('/adx/advertisers', {
      advertiserName: 'A',
    });
    expect(requestClient.put).toHaveBeenCalledWith('/adx/advertisers/adv-1', {
      advertiserName: 'A',
      id: 'adv-1',
    });
  });

  it('keeps report filters and clue batches connected to backend contracts', () => {
    getReport({ granularity: 'DATE', startDate: '2026-08-01' });
    getReportOptions();
    exportReport({ granularity: 'HOUR' });
    updateClueStatus(['clue-1'], 0);
    updateClueStatusByFilter({
      endDate: '2026-08-02',
      sourceStatus: 2,
      startDate: '2026-08-01',
      status: 0,
    });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/reports/hourly', {
      params: { granularity: 'DATE', startDate: '2026-08-01' },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/report-options');
    expect(requestClient.put).toHaveBeenCalledWith('/adx/clues/batch-status', {
      ids: ['clue-1'],
      status: 0,
    });
    expect(requestClient.put).toHaveBeenCalledWith('/adx/clues/batch-status', {
      endDate: '2026-08-02',
      mode: 'FILTER',
      sourceStatus: 2,
      startDate: '2026-08-01',
      status: 0,
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/reports/hourly/export',
      {
        params: { granularity: 'HOUR' },
        responseReturn: 'body',
        responseType: 'blob',
      },
    );
  });
});
