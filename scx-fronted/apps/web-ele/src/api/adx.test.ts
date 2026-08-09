import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  authorizeApiDebug,
  executeApiDebug,
  exportReport,
  getAdvertiserLedger,
  getApiDebugExecutionLogs,
  getApiDebugRecentLogs,
  getApiDebugTargets,
  getCatalog,
  getChannelEventOptions,
  getDashboardAccountSummary,
  getDashboardSummary,
  getEventDefinitions,
  getEventMappings,
  getMonitor,
  getMonitorIntegration,
  getReport,
  getReportOptions,
  getRuntimeDeliveryAttempts,
  getRuntimeEvents,
  getRuntimeEventTimeline,
  getRuntimeIngressAudits,
  preflightApiDebug,
  rechargeAdvertiser,
  saveCatalog,
  saveEventDefinition,
  saveEventMapping,
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

  it('sends the same effect filters to typed dashboard and protected account summaries', () => {
    const filters = {
      advertiserId: 'adv-1',
      endDate: '2026-08-09',
      startDate: '2026-08-01',
    };
    getDashboardSummary(filters);
    getDashboardAccountSummary(filters);
    expect(requestClient.get).toHaveBeenCalledWith('/adx/dashboard/summary', {
      params: filters,
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/dashboard/account-summary',
      { params: filters },
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

  it('loads the safe integration guide for one monitor', () => {
    getMonitorIntegration('monitor-1');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/monitors/monitor-1/integration',
    );
  });

  it('connects event management, channel options and monitor detail contracts', () => {
    getEventDefinitions({ category: 'UPSTREAM', channelId: 'channel-1' });
    getEventMappings({ channelId: 'channel-1' });
    getChannelEventOptions('channel-1');
    getMonitor('monitor-1');
    saveEventDefinition({
      category: 'CONVERSION',
      eventCode: 'LEAD',
      eventName: '线索',
    });
    saveEventMapping({ channelId: 'channel-1', upstreamEventId: 'upstream-1' });

    expect(requestClient.get).toHaveBeenCalledWith('/adx/events', {
      params: { category: 'UPSTREAM', channelId: 'channel-1' },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/adx/event-mappings', {
      params: { channelId: 'channel-1' },
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/channels/channel-1/event-options',
    );
    expect(requestClient.get).toHaveBeenCalledWith('/adx/monitors/monitor-1');
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/events',
      expect.objectContaining({ eventCode: 'LEAD' }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/event-mappings',
      expect.objectContaining({ channelId: 'channel-1' }),
    );
  });

  it('connects debugger preflight, one-time authorization and execution', () => {
    const request = {
      body: '{"event":"CLICK"}',
      bodyType: 'JSON' as const,
      headers: { Accept: 'application/json' },
      method: 'POST' as const,
      mode: 'INTEGRATED' as const,
      monitorId: 'monitor-1',
      scenario: 'DOWNSTREAM_TO_UPSTREAM' as const,
      timeoutSeconds: 5,
      url: 'locked-target.example.com',
    };
    getApiDebugTargets();
    preflightApiDebug(request);
    authorizeApiDebug({
      confirmationPhrase: 'EXECUTE:draft-1',
      draftId: 'draft-1',
      password: 'not-a-real-password',
      purpose: 'EXECUTE',
    });
    executeApiDebug('draft-1', 'one-time-token');
    getApiDebugExecutionLogs('draft-1', 9);

    expect(requestClient.get).toHaveBeenCalledWith('/adx/api-debug/targets', {
      params: { keyword: undefined },
    });
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/api-debug/preflight',
      request,
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/api-debug/authorizations',
      {
        confirmationPhrase: 'EXECUTE:draft-1',
        draftId: 'draft-1',
        password: 'not-a-real-password',
        purpose: 'EXECUTE',
      },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/adx/api-debug/executions',
      { authorizationToken: 'one-time-token', draftId: 'draft-1' },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/api-debug/executions/draft-1/logs',
      { params: { afterSequence: 9 } },
    );
  });

  it('keeps raw log filters behind the dedicated authorization token', () => {
    getApiDebugRecentLogs('short-lived-token', {
      cursor: 12,
      keyword: 'delivery',
      level: 'ERROR',
      source: 'ENGINE',
    });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/adx/api-debug/logs/recent',
      {
        headers: {
          'X-SCX-Debug-Authorization': 'short-lived-token',
        },
        params: {
          cursor: 12,
          keyword: 'delivery',
          level: 'ERROR',
          source: 'ENGINE',
        },
      },
    );
  });
});
