import { describe, expect, it } from 'vitest';

import {
  canConfirmRuntimeReplay,
  resolveRuntimePageState,
  runtimeValueIsMasked,
} from './runtime-model';

describe('runtime center state model', () => {
  it('keeps first empty, filter empty, loading, error and forbidden distinct', () => {
    const base = {
      error: false,
      filtersActive: false,
      forbidden: false,
      initialized: true,
      loading: false,
      rowCount: 0,
    };
    expect(resolveRuntimePageState(base)).toBe('INITIAL_EMPTY');
    expect(resolveRuntimePageState({ ...base, filtersActive: true })).toBe(
      'FILTER_EMPTY',
    );
    expect(
      resolveRuntimePageState({ ...base, initialized: false, loading: true }),
    ).toBe('LOADING');
    expect(resolveRuntimePageState({ ...base, error: true })).toBe(
      'REQUEST_ERROR',
    );
    expect(resolveRuntimePageState({ ...base, forbidden: true })).toBe(
      'FORBIDDEN',
    );
  });

  it('requires an unblocked original-snapshot preview, reason and secondary auth', () => {
    const preview = {
      billingEffect: 'idempotent',
      blockers: [],
      errorCode: 'TIMEOUT',
      expiresAt: '2026-08-10T12:00:00',
      externalEffect: 'async',
      failureClass: 'RETRYABLE',
      failureId: 'failure-1',
      failureRevision: 1,
      mode: 'ORIGINAL_SNAPSHOT_ONLY' as const,
      previewToken: 'signed',
      replayable: true,
      routeVersion: 3,
      routeVersionId: 'version-3',
      taskId: 'task-1',
      traceId: 'trace-1',
      workerStatus: 'DISABLED' as const,
    };
    expect(
      canConfirmRuntimeReplay({
        password: 'verified-password',
        preview,
        reason: 'approved compensation',
      }),
    ).toBe(true);
    expect(
      canConfirmRuntimeReplay({
        password: '',
        preview,
        reason: 'approved compensation',
      }),
    ).toBe(false);
    expect(
      canConfirmRuntimeReplay({
        password: 'verified-password',
        preview: { ...preview, blockers: ['snapshot missing'] },
        reason: 'approved compensation',
      }),
    ).toBe(false);
  });

  it('accepts only omitted or server-masked external identifiers', () => {
    expect(runtimeValueIsMasked(undefined)).toBe(true);
    expect(runtimeValueIsMasked('9fd4c3a1…')).toBe(true);
    expect(runtimeValueIsMasked('full-device-value')).toBe(false);
  });
});
