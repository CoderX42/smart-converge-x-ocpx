import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  exportReport,
  getAdvertiserLedger,
  getCatalog,
  getReport,
  getReportOptions,
  getRuntimeDeliveryAttempts,
  getRuntimeEvents,
  getRuntimeEventTimeline,
  getRuntimeIngressAudits,
  rechargeAdvertiser,
  saveCatalog,
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

  it('keeps report filters and idempotent recharge connected to backend contracts', () => {
    getReport({ granularity: 'DATE', startDate: '2026-08-01' });
    getReportOptions();
    exportReport({ granularity: 'HOUR' });
    rechargeAdvertiser('adv-1', 100, 'recharge_20260807_0001');
    getAdvertiserLedger('adv-1', { current: 1, size: 20 });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/reports/hourly', {
      params: { granularity: 'DATE', startDate: '2026-08-01' },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/report-options');
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/advertisers/adv-1/recharge',
      {
        amount: 100,
        requestId: 'recharge_20260807_0001',
      },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/advertisers/adv-1/ledger',
      { params: { current: 1, size: 20 } },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/reports/hourly/export',
      {
        params: { granularity: 'HOUR' },
        responseReturn: 'body',
        responseType: 'blob',
      },
    );
  });

  it('connects event, timeline, attempt and ingress audit runtime views', () => {
    getRuntimeEvents({ current: 1, eventType: 'CLICK', size: 20 });
    getRuntimeEventTimeline('event-row-1');
    getRuntimeDeliveryAttempts('delivery-1');
    getRuntimeIngressAudits({ resultStatus: 'AUTH_REJECTED' });

    expect(requestClient.get).toHaveBeenCalledWith('/adx/runtime/events', {
      params: { current: 1, eventType: 'CLICK', size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/runtime/events/event-row-1/timeline',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/runtime/deliveries/delivery-1/attempts',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/runtime/ingress-audits',
      { params: { resultStatus: 'AUTH_REJECTED' } },
    );
  });
});
