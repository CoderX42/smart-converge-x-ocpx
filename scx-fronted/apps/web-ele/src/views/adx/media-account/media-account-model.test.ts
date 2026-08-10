import { describe, expect, it } from 'vitest';

import {
  buildMediaAccountCreatePayload,
  buildMediaAccountUpdatePayload,
  mediaAccountEditorSnapshot,
  resetCredentialEditor,
  resolveMediaAccountEmptyState,
} from './media-account-model';

describe('media account management model', () => {
  it('distinguishes no-permission, initial, filtered and failure states', () => {
    expect(
      resolveMediaAccountEmptyState({
        canView: false,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveMediaAccountEmptyState({
        canView: true,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveMediaAccountEmptyState({
        canView: true,
        error: false,
        filtersActive: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveMediaAccountEmptyState({
        canView: true,
        error: true,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('normalizes create/update payload and snapshots only non-secret account fields', () => {
    const editor = {
      channelId: 'channel-1',
      description: ' description ',
      documentId: '',
      name: ' Media Account ',
    };
    expect(buildMediaAccountCreatePayload(editor)).toEqual({
      channelId: 'channel-1',
      description: 'description',
      documentId: undefined,
      name: 'Media Account',
    });
    expect(
      buildMediaAccountUpdatePayload(editor, {
        adapterCode: 'STANDARD',
        channelId: 'channel-1',
        channelName: 'Channel',
        code: 'MA_1',
        configVersion: 6,
        createTime: '2026-08-10T00:00:00',
        credentialRevision: 2,
        documentVersion: 0,
        id: 'media-1',
        name: 'Media Account',
        references: { publishedRouteCount: 0, routeCount: 0, taskCount: 0 },
        status: 'DISABLED',
        updateTime: '2026-08-10T00:00:00',
      }).expectedVersion,
    ).toBe(6);
    expect(mediaAccountEditorSnapshot(editor)).not.toContain('secret');
  });

  it('clears password and Secret immediately after a credential action', () => {
    const editor = {
      description: 'primary',
      loginIdentifier: 'account@example.invalid',
      password: 'local-password',
      reason: 'rotation',
      secretInput: 'local-secret',
    };
    resetCredentialEditor(editor);
    expect(editor).toEqual({
      description: '',
      loginIdentifier: '',
      password: '',
      reason: '',
      secretInput: '',
    });
  });
});
