import { describe, expect, it } from 'vitest';

import {
  appLinkEditorSnapshot,
  appLinkRedirectIssue,
  buildAppLinkCreatePayload,
  buildAppLinkUpdatePayload,
  resolveAppLinkEmptyState,
} from './app-link-model';

describe('app link management model', () => {
  const editor = {
    clearDeeplink: false,
    clearDownload: false,
    deeplinkUrl: ' https://8.8.4.4/open ',
    description: ' description ',
    documentId: 'document-1',
    downloadUrl: '',
    linkName: ' Main link ',
    linkType: 'ANDROID' as const,
    linkUrlTemplate: ' https://8.8.4.4/click?clickid=__CLICKID__ ',
    productId: 'product-1',
    redirectEnabled: true,
  };

  it('distinguishes permission, initial, filtered and failed states', () => {
    expect(
      resolveAppLinkEmptyState({
        canView: false,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveAppLinkEmptyState({
        canView: true,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveAppLinkEmptyState({
        canView: true,
        error: false,
        filtersActive: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveAppLinkEmptyState({
        canView: true,
        error: true,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('trims URL inputs and keeps optimistic-lock and clear semantics explicit', () => {
    const createPayload = buildAppLinkCreatePayload(editor);
    expect(createPayload).toMatchObject({
      deeplinkUrl: 'https://8.8.4.4/open',
      linkName: 'Main link',
      linkUrlTemplate: 'https://8.8.4.4/click?clickid=__CLICKID__',
    });
    expect(createPayload).not.toHaveProperty('status');
    const updatePayload = buildAppLinkUpdatePayload(editor, {
      advertiserId: 'advertiser-1',
      advertiserName: 'Advertiser',
      configVersion: 7,
      createTime: '',
      deeplinkConfigured: true,
      documentVersion: 1,
      downloadConfigured: false,
      id: 'link-1',
      linkName: 'Main link',
      macroSet: [],
      mappingStatus: 'READY',
      redirectEnabled: true,
      references: {
        deductionRuleCount: 0,
        monitorCount: 0,
        publishedRouteCount: 0,
        republishRequiredCount: 0,
      },
      republishRequiredCount: 0,
      status: 'ACTIVE',
      templateConfigured: true,
      updateTime: '',
      validationStatus: 'PASS',
    });
    expect(updatePayload.expectedVersion).toBe(7);
    expect(updatePayload).not.toHaveProperty('status');
    expect(appLinkEditorSnapshot(editor)).not.toContain(' Main link ');
  });

  it('requires a redirect target when redirect is enabled', () => {
    expect(appLinkRedirectIssue(editor)).toBe('');
    expect(
      appLinkRedirectIssue({
        ...editor,
        clearDeeplink: true,
        deeplinkUrl: '',
      }),
    ).toContain('至少填写一个');
  });
});
