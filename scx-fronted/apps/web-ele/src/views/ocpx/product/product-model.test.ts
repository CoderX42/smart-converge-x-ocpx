import { describe, expect, it } from 'vitest';

import {
  buildProductCreatePayload,
  buildProductUpdatePayload,
  duplicateProductPolicyIndexes,
  productEditorSnapshot,
  resolveProductEmptyState,
} from './product-model';

describe('product management model', () => {
  it('distinguishes permission, initial, filtered and failed states', () => {
    expect(
      resolveProductEmptyState({
        canView: false,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveProductEmptyState({
        canView: true,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveProductEmptyState({
        canView: true,
        error: false,
        filtersActive: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveProductEmptyState({
        canView: true,
        error: true,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('pinpoints every duplicate event row', () => {
    expect(
      duplicateProductPolicyIndexes([
        { dailyLimit: 0, eventDefinitionId: 'event-1' },
        { dailyLimit: 10, eventDefinitionId: 'event-2' },
        { dailyLimit: 20, eventDefinitionId: 'event-1' },
      ]),
    ).toEqual([0, 2]);
  });

  it('builds stable create/update payloads and snapshots file metadata only', () => {
    const logo = new File(['content'], 'logo.png', {
      lastModified: 10,
      type: 'image/png',
    });
    const editor = {
      advertiserId: 'advertiser-1',
      code: ' product_code ',
      description: ' description ',
      eventPolicies: [{ dailyLimit: 0, eventDefinitionId: 'event-1' }],
      logoFile: logo,
      name: ' Product ',
      packageName: ' com.example.app ',
      removeLogo: false,
      status: 'ACTIVE' as const,
    };
    expect(buildProductCreatePayload(editor)).toMatchObject({
      code: 'product_code',
      name: 'Product',
      packageName: 'com.example.app',
    });
    expect(
      buildProductUpdatePayload(editor, {
        advertiserId: 'advertiser-1',
        advertiserName: 'Advertiser',
        code: 'product_code',
        configVersion: 4,
        createTime: '2026-08-10T00:00:00',
        eventPolicies: [],
        eventPolicyRevision: 2,
        id: 'product-1',
        logoAvailable: false,
        name: 'Product',
        references: { appLinkCount: 0, routeCount: 0 },
        status: 'ACTIVE',
        updateTime: '2026-08-10T00:00:00',
      }).expectedVersion,
    ).toBe(4);
    expect(productEditorSnapshot(editor)).toContain('logo.png');
    expect(productEditorSnapshot(editor)).not.toContain('content');
  });
});
