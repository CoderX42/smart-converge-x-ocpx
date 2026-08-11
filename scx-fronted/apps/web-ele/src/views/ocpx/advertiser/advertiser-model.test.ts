import { describe, expect, it } from 'vitest';

import {
  buildAdvertiserCreatePayload,
  buildAdvertiserUpdatePayload,
  editorSnapshot,
  resolveAdvertiserEmptyState,
} from './advertiser-model';

describe('advertiser management model', () => {
  it('distinguishes permission, initial, filtered and error empty states', () => {
    expect(
      resolveAdvertiserEmptyState({
        canView: false,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveAdvertiserEmptyState({
        canView: true,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveAdvertiserEmptyState({
        canView: true,
        error: false,
        filtersActive: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveAdvertiserEmptyState({
        canView: true,
        error: true,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('keeps credentials only in create and never in ordinary update payloads', () => {
    const form = {
      account: ' account ',
      adapterKey: 'COCO',
      advertiserName: ' Advertiser ',
      bidUrl: 'https://example.test/callback',
      code: 'adv_test',
      credentialInput: 'write-only-value',
      password: 'password-123',
      upstreamHostAllowlist: 'example.test',
    };
    expect(buildAdvertiserCreatePayload(form).credentialInput).toBe(
      'write-only-value',
    );
    const update = buildAdvertiserUpdatePayload(form, {
      accountMasked: 'a***t',
      adapterKey: 'COCO',
      adapterVersion: 1,
      advertiserName: 'Advertiser',
      balance: 0,
      code: 'adv_test',
      configVersion: 4,
      createTime: '2026-08-10T00:00:00',
      credentialConfigured: true,
      credentialRevision: 2,
      documentRevision: 0,
      id: 'adv-1',
      republishRequiredCount: 0,
      status: 'DISABLED',
      updateTime: '2026-08-10T00:00:00',
    });
    expect(update).not.toHaveProperty('credentialInput');
    expect(update).not.toHaveProperty('password');
    expect(update.expectedVersion).toBe(4);
    expect(editorSnapshot(form)).not.toContain('write-only-value');
    expect(editorSnapshot(form)).not.toContain('password-123');
  });
});
