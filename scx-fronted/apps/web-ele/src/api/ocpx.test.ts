import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  archiveAppLink,
  archiveProduct,
  authorizeApiDebug,
  changeAdvertiserStatus,
  changeAppLinkStatus,
  changeMediaCredentialStatus,
  completeAppLinkMapping,
  confirmRuntimeReplay,
  createAdvertiser,
  createAppLink,
  createMediaAccount,
  createMediaCredential,
  createProduct,
  downloadAdvertiserDocument,
  executeApiDebug,
  exportReport,
  generateOcpxBusinessCode,
  getAccessScopeOptions,
  getAdvertiserAuthorization,
  getAdvertiserAuthorizationOptions,
  getAdvertiserDetail,
  getAdvertiserDocuments,
  getAdvertiserLedger,
  getAdvertisers,
  getApiDebugExecutionLogs,
  getApiDebugRecentLogs,
  getApiDebugTargets,
  getAppLink,
  getAppLinkDocumentOptions,
  getAppLinkProductOptions,
  getAppLinks,
  getCatalog,
  getChannelEventOptions,
  getDashboardOptions,
  getDashboardSummary,
  getEventDefinitions,
  getEventMappings,
  getMediaAccount,
  getMediaAccounts,
  getMediaCredentials,
  getMonitor,
  getMonitorIntegration,
  getProduct,
  getProductAdvertiserOptions,
  getProductEventOptions,
  getProducts,
  getReport,
  getReportOptions,
  getRuntimeExecution,
  getRuntimeFacts,
  getRuntimeFailures,
  getUserAccessScopeImpact,
  getUserAccessScopes,
  preflightApiDebug,
  preflightMediaAccount,
  previewRuntimeReplay,
  rechargeAdvertiser,
  replaceUserAccessScopes,
  restoreAppLink,
  rotateAdvertiserCredential,
  rotateMediaCredential,
  saveEventDefinition,
  saveEventMapping,
  updateAdvertiserAuthorization,
  updateProduct,
  uploadProductLogo,
  validateAppLinkUrl,
} from './ocpx';

const requestClient = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('ocpx API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('queries the compact channel endpoint with pagination', () => {
    getCatalog('channel', { current: 2, size: 20 });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/channels', {
      params: { current: 2, size: 20 },
    });
  });

  it('uses the revision-aware advertiser API without a secret read endpoint', () => {
    const payload = {
      account: 'account',
      code: 'adv_test',
      extraMenuIds: ['perm_ocpx_report_view'],
      integration: {
        adapterKey: 'COCO',
        bidUrl: 'https://example.test/callback',
        credentialInput: 'write-only-credential',
        upstreamHostAllowlist: 'example.test',
      },
      name: 'Advertiser',
      password: 'password-123',
      roleIds: ['role_analyst'],
    };
    getAdvertisers({ current: 1, documentConfigured: true, size: 20 });
    getAdvertiserDetail('adv-1');
    createAdvertiser(payload, 'create-request-0001');
    rotateAdvertiserCredential('adv-1', {
      credentialInput: 'replacement-credential',
      expectedCredentialRevision: 1,
      reason: 'scheduled rotation',
    });
    changeAdvertiserStatus('adv-1', {
      confirmed: true,
      expectedVersion: 3,
      reason: 'ready',
      status: 'ACTIVE',
    });
    getAdvertiserDocuments('adv-1');
    downloadAdvertiserDocument('adv-1', 'doc-1');

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/advertisers', {
      params: { current: 1, documentConfigured: true, size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/advertisers/adv-1');
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers',
      payload,
      { headers: { 'Idempotency-Key': 'create-request-0001' } },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/credentials:rotate',
      expect.objectContaining({ expectedCredentialRevision: 1 }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/status',
      expect.objectContaining({ expectedVersion: 3 }),
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/documents',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/documents/doc-1/content',
      { responseReturn: 'body', responseType: 'blob' },
    );
  });

  it('uses dedicated advertiser authorization and business-code endpoints', () => {
    getAdvertiserAuthorizationOptions();
    getAdvertiserAuthorization('adv-1');
    updateAdvertiserAuthorization('adv-1', {
      expectedGrantVersion: 4,
      extraMenuIds: ['perm_ocpx_report_view'],
      roleIds: ['role_analyst'],
    });
    generateOcpxBusinessCode('ADVERTISER');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/login-authorization/options',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/login-authorization',
    );
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/login-authorization',
      expect.objectContaining({ expectedGrantVersion: 4 }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/business-codes/generate',
      { resourceType: 'ADVERTISER' },
    );
  });

  it('uses the scoped revision-aware Product API and managed Logo endpoint', () => {
    const payload = {
      advertiserId: 'adv-1',
      code: 'product_code',
      eventPolicies: [{ dailyLimit: 0, eventDefinitionId: 'event-1' }],
      name: 'Product',
      status: 'ACTIVE' as const,
    };
    getProducts({ current: 1, size: 20 });
    getProduct('product-1');
    getProductAdvertiserOptions();
    getProductEventOptions();
    createProduct(payload, 'product-request-0001');
    updateProduct('product-1', {
      eventPolicies: payload.eventPolicies,
      expectedVersion: 2,
      name: 'Product v2',
      status: 'ACTIVE',
    });
    archiveProduct('product-1', 3, 'obsolete');
    const logo = new File(['logo'], 'logo.png', { type: 'image/png' });
    uploadProductLogo('product-1', 3, logo);

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/products', {
      params: { current: 1, size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/products/product-1');
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/products/advertiser-options',
      { params: { keyword: undefined } },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/products/event-options',
      { params: { keyword: undefined } },
    );
    expect(requestClient.post).toHaveBeenCalledWith('/ocpx/products', payload, {
      headers: { 'Idempotency-Key': 'product-request-0001' },
    });
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/products/product-1',
      expect.objectContaining({ expectedVersion: 2 }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/products/product-1/archive',
      { expectedVersion: 3, reason: 'obsolete' },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/products/product-1/logo',
      expect.any(FormData),
      { params: { expectedVersion: 3 } },
    );
  });

  it('uses revision-aware media accounts and write-only credential endpoints', () => {
    getMediaAccounts({ current: 1, size: 20 });
    getMediaAccount('media-1');
    createMediaAccount(
      { channelId: 'channel-1', name: 'Media Account' },
      'media-request-0001',
    );
    getMediaCredentials('media-1');
    createMediaCredential('media-1', {
      loginIdentifier: 'account@example.invalid',
      loginType: 'EMAIL',
      secretInput: 'write-only-secret',
    });
    rotateMediaCredential('media-1', 'credential-1', {
      expectedSecretRevision: 1,
      reason: 'scheduled rotation',
      secretInput: 'replacement-secret',
    });
    changeMediaCredentialStatus('media-1', 'credential-1', {
      expectedVersion: 2,
      status: 'ACTIVE',
    });
    preflightMediaAccount('media-1', 4);

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/media-accounts', {
      params: { current: 1, size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/media-accounts/media-1',
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/media-accounts',
      { channelId: 'channel-1', name: 'Media Account' },
      { headers: { 'Idempotency-Key': 'media-request-0001' } },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/media-accounts/media-1/credentials/credential-1:rotate',
      expect.objectContaining({ expectedSecretRevision: 1 }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/media-accounts/media-1/connectivity:check',
      { expectedVersion: 4 },
    );
  });

  it('uses the structured AppLink API without fetching or exposing full stored URLs', () => {
    const linkInput = {
      deeplinkUrl: 'https://deeplink.example.invalid/open',
      documentId: 'document-1',
      downloadUrl: 'https://download.example.invalid/app',
      linkName: 'Primary link',
      linkType: 'ANDROID' as const,
      linkUrlTemplate:
        'https://landing.example.invalid/open?click_id={click_id}',
      productId: 'product-1',
      redirectEnabled: true,
    };

    getAppLinks({ current: 1, mappingStatus: 'READY', size: 20 });
    getAppLink('link-1');
    getAppLinkProductOptions({ keyword: 'Product' });
    getAppLinkDocumentOptions('product-1');
    createAppLink(linkInput, 'link-request-0001');
    completeAppLinkMapping('legacy-link-1', {
      ...linkInput,
      expectedVersion: 1,
    });
    changeAppLinkStatus('link-1', 2, 'DISABLED', 'maintenance');
    archiveAppLink('link-1', 3, 'obsolete');
    restoreAppLink('link-1', 4, 'restore');
    validateAppLinkUrl({
      kind: 'TEMPLATE',
      linkType: 'ANDROID',
      url: linkInput.linkUrlTemplate,
    });

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/app-links', {
      params: { current: 1, mappingStatus: 'READY', size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/app-links/link-1');
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/app-links/product-options',
      { params: { keyword: 'Product' } },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/app-links/document-options',
      { params: { productId: 'product-1' } },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links',
      linkInput,
      { headers: { 'Idempotency-Key': 'link-request-0001' } },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links/legacy-link-1/mapping:complete',
      expect.objectContaining({ expectedVersion: 1, productId: 'product-1' }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links/link-1/status',
      expect.objectContaining({ expectedVersion: 2, status: 'DISABLED' }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links/link-1/archive',
      { expectedVersion: 3, reason: 'obsolete' },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links/link-1/restore',
      { expectedVersion: 4, reason: 'restore' },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/app-links/url:validate',
      expect.objectContaining({ kind: 'TEMPLATE' }),
    );
  });

  it('keeps report filters and idempotent recharge connected to backend contracts', () => {
    const reportQuery = {
      endDate: '2026-08-07',
      grain: 'DAY' as const,
      groupBy: 'TIME' as const,
      startDate: '2026-08-01',
    };
    getReport(reportQuery);
    getReportOptions();
    exportReport({ ...reportQuery, grain: 'HOUR' });
    rechargeAdvertiser('adv-1', 100, 'recharge_20260807_0001');
    getAdvertiserLedger('adv-1', { current: 1, size: 20 });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/reports/hourly', {
      params: reportQuery,
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/report-options');
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/recharge',
      {
        amount: 100,
        requestId: 'recharge_20260807_0001',
      },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/adv-1/ledger',
      { params: { current: 1, size: 20 } },
    );
    expect(requestClient.post).toHaveBeenCalledWith('/ocpx/reports/exports', {
      ...reportQuery,
      grain: 'HOUR',
    });
  });

  it('uses one typed Dashboard summary and its scope-filtered options', () => {
    const filters = {
      advertiserId: 'adv-1',
      endDate: '2026-08-09',
      startDate: '2026-08-01',
    };
    getDashboardSummary(filters);
    getDashboardOptions();
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/dashboard/summary', {
      params: filters,
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/dashboard/options');
  });

  it('connects sanitized facts, DLQ, timeline and two-stage replay', () => {
    getRuntimeFacts({ current: 1, factType: 'CLICK', size: 20 });
    getRuntimeFailures({ failureClass: 'RETRYABLE' });
    getRuntimeExecution('fact-1');
    previewRuntimeReplay({
      expectedFailureRevision: 3,
      failureId: 'failure-1',
    });
    confirmRuntimeReplay({
      confirmed: true,
      expectedFailureRevision: 3,
      failureId: 'failure-1',
      previewToken: 'signed-preview',
      reason: 'approved compensation',
      requestId: 'request_1234567890',
    });

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/runtime/facts', {
      params: { current: 1, factType: 'CLICK', size: 20 },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/runtime/dlq', {
      params: { failureClass: 'RETRYABLE' },
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/runtime/executions/fact-1',
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/runtime/replays:preview',
      { expectedFailureRevision: 3, failureId: 'failure-1' },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/runtime/replays',
      expect.objectContaining({ requestId: 'request_1234567890' }),
    );
  });

  it('loads the safe integration guide for one monitor', () => {
    getMonitorIntegration('monitor-1');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/monitors/monitor-1/integration',
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

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/events', {
      params: { category: 'UPSTREAM', channelId: 'channel-1' },
    });
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/event-mappings', {
      params: { channelId: 'channel-1' },
    });
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/channels/channel-1/event-options',
    );
    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/monitors/monitor-1');
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/events',
      expect.objectContaining({ eventCode: 'LEAD' }),
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/event-mappings',
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

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/api-debug/targets', {
      params: { keyword: undefined },
    });
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/api-debug/preflight',
      request,
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/api-debug/authorizations',
      {
        confirmationPhrase: 'EXECUTE:draft-1',
        draftId: 'draft-1',
        password: 'not-a-real-password',
        purpose: 'EXECUTE',
      },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/api-debug/executions',
      { authorizationToken: 'one-time-token', draftId: 'draft-1' },
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/api-debug/executions/draft-1/logs',
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
      '/ocpx/api-debug/logs/recent',
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

  it('uses a versioned whole-set contract for user object scopes', () => {
    getUserAccessScopes('user-1');
    getUserAccessScopeImpact('user-1');
    getAccessScopeOptions();
    replaceUserAccessScopes('user-1', 3, [
      {
        permissionLevel: 'VIEW',
        scopeId: 'channel-1',
        scopeType: 'CHANNEL',
      },
    ]);

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/access-scopes/users/user-1',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/access-scopes/users/user-1/impact',
    );
    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/access-scopes/options',
    );
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/access-scopes/users/user-1',
      {
        scopes: [
          {
            permissionLevel: 'VIEW',
            scopeId: 'channel-1',
            scopeType: 'CHANNEL',
          },
        ],
        version: 3,
      },
    );
  });
});
