import { describe, expect, it } from 'vitest';

import {
  compatibleDefinitions,
  resolveEventPageState,
  statusNeedsReferenceConfirmation,
} from './event-model';

describe('canonical event page model', () => {
  it('distinguishes permission, first-empty, filtered-empty and failure states', () => {
    expect(
      resolveEventPageState({
        canView: false,
        error: false,
        filtered: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveEventPageState({
        canView: true,
        error: false,
        filtered: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveEventPageState({
        canView: true,
        error: false,
        filtered: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveEventPageState({
        canView: true,
        error: true,
        filtered: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('filters definitions by exact direction without guessing', () => {
    const definitions = [
      { direction: 'DOWNSTREAM_DELIVERY', id: 'd1', status: 'ACTIVE' },
      { direction: 'UPSTREAM_CALLBACK', id: 'd2', status: 'ACTIVE' },
      { direction: 'BIDIRECTIONAL', id: 'd3', status: 'ACTIVE' },
      { direction: 'DOWNSTREAM_DELIVERY', id: 'd4', status: 'DISABLED' },
    ] as any;
    expect(
      compatibleDefinitions(definitions, 'DOWNSTREAM_DELIVERY').map(
        (item) => item.id,
      ),
    ).toEqual(['d1', 'd3']);
  });

  it('requires explicit confirmation before disabling referenced objects', () => {
    const row = {
      references: { statusChangeRequiresConfirmation: true },
      status: 'ACTIVE',
    } as any;
    expect(statusNeedsReferenceConfirmation(row, 'DISABLED')).toBe(true);
  });
});
